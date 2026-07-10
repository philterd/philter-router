/*
 * Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.router.watch;

import ai.philterd.router.audit.AuditLogger;
import ai.philterd.router.classify.Classifier;
import ai.philterd.router.config.EngineConfig;
import ai.philterd.router.config.Outcome;
import ai.philterd.router.config.RouterConfig;
import ai.philterd.router.config.WatchLocation;
import ai.philterd.router.engine.EngineRegistry;
import ai.philterd.router.extract.TextExtractor;
import ai.philterd.router.lang.LanguageDetector;
import ai.philterd.router.metrics.RouterMetrics;
import ai.philterd.router.model.AttributeSources;
import ai.philterd.router.routing.DefaultAttributeSources;
import ai.philterd.router.routing.Router;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration test: the folder-watcher processing pipeline against a stubbed Philter. */
class FileProcessorIT {

    // Loaded once; the language model is not consulted here but the source is real.
    private static final LanguageDetector LANGUAGE = new LanguageDetector(0.05);
    private static final TextExtractor EXTRACTOR = new TextExtractor(20_000);

    private MockWebServer philter;
    private FileProcessor processor;
    private WatchLocation location;

    @TempDir
    Path work;

    @BeforeEach
    void setUp() throws Exception {
        philter = new MockWebServer();
        philter.start();

        final RouterConfig config = new RouterConfig();
        final EngineConfig engine = new EngineConfig();
        engine.url = philter.url("/").toString();
        config.engines = Map.of("philter1", engine);
        final Outcome def = new Outcome();
        def.engine = "philter1";
        def.policy = "default";
        config.defaultOutcome = def;
        config.routes = List.of();

        final AttributeSources sources =
                new DefaultAttributeSources(EXTRACTOR, LANGUAGE, new Classifier(), Map.of());
        processor = new FileProcessor(new Router(config), new EngineRegistry(config.engines), sources,
                new ProcessedLedger(), new AuditLogger(), new RouterMetrics(new SimpleMeterRegistry()));

        final Path in = work.resolve("in");
        Files.createDirectories(in);
        location = new WatchLocation();
        location.path = in.toString();
        location.output = work.resolve("out").toString();
        location.done = work.resolve("done").toString();
        location.error = work.resolve("error").toString();
        location.stableForMs = 20;
    }

    @AfterEach
    void tearDown() throws Exception {
        philter.shutdown();
    }

    private Path in(final String name) {
        return Path.of(location.path).resolve(name);
    }

    @Test
    void redactsAndDrainsToDone() throws Exception {
        philter.enqueue(new MockResponse().setBody("REDACTED"));
        final Path file = in("a.txt");
        Files.writeString(file, "His SSN was 123-45-6789.");

        processor.process(file, location);

        final Path out = Path.of(location.output).resolve("a.txt");
        assertTrue(Files.exists(out), "redacted output should be written");
        assertEquals("REDACTED", Files.readString(out));
        assertFalse(Files.exists(file), "source should be moved out of the watched directory");
        assertTrue(Files.exists(Path.of(location.done).resolve("a.txt")), "source should be moved to done");
        assertEquals(1, philter.getRequestCount());
    }

    @Test
    void movesToErrorWhenEngineFails() throws Exception {
        philter.enqueue(new MockResponse().setResponseCode(500));
        final Path file = in("b.txt");
        Files.writeString(file, "data");

        processor.process(file, location);

        assertTrue(Files.exists(Path.of(location.error).resolve("b.txt")), "failed source should move to error");
        assertFalse(Files.exists(Path.of(location.output).resolve("b.txt")), "no output on failure");
    }

    @Test
    void rejectsUnmatchedToErrorWithoutCallingEngine() throws Exception {
        final RouterConfig rejectConfig = new RouterConfig();
        final EngineConfig engine = new EngineConfig();
        engine.url = philter.url("/").toString();
        rejectConfig.engines = Map.of("philter1", engine);
        final Outcome def = new Outcome();
        def.action = "reject";
        rejectConfig.defaultOutcome = def;
        rejectConfig.routes = List.of();

        final AttributeSources sources =
                new DefaultAttributeSources(EXTRACTOR, LANGUAGE, new Classifier(), Map.of());
        final FileProcessor rejectProcessor = new FileProcessor(new Router(rejectConfig),
                new EngineRegistry(rejectConfig.engines), sources, new ProcessedLedger(), new AuditLogger(),
                new RouterMetrics(new SimpleMeterRegistry()));

        final Path file = in("c.txt");
        Files.writeString(file, "unmatched content");
        rejectProcessor.process(file, location);

        assertTrue(Files.exists(Path.of(location.error).resolve("c.txt")), "rejected source should move to error");
        assertFalse(Files.exists(Path.of(location.output).resolve("c.txt")), "no output for a rejected file");
        assertEquals(0, philter.getRequestCount(), "a rejected file must not reach the engine");
    }

    @Test
    void identicalContentIsProcessedOnce() throws Exception {
        philter.enqueue(new MockResponse().setBody("R"));
        final Path a = in("a.txt");
        Files.writeString(a, "same content");
        processor.process(a, location);

        // A different filename with identical content must not be redacted again.
        final Path b = in("b.txt");
        Files.writeString(b, "same content");
        processor.process(b, location);

        assertEquals(1, philter.getRequestCount(), "identical content should reach the engine only once");
        assertTrue(Files.exists(Path.of(location.done).resolve("b.txt")), "the duplicate is still drained to done");
    }

}
