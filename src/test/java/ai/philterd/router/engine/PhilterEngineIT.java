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
package ai.philterd.router.engine;

import ai.philterd.philter.model.BinaryFilterResponse;
import ai.philterd.philter.model.FilterResponse;
import ai.philterd.router.config.EngineConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration test: PhilterEngine against a stubbed Philter, verifying request shape and auth forwarding. */
class PhilterEngineIT {

    private MockWebServer philter;
    private PhilterEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        philter = new MockWebServer();
        philter.start();
        final EngineConfig config = new EngineConfig();
        config.url = philter.url("/").toString();
        config.apiKey = "sk_configuredkey";
        engine = new PhilterEngine("philter1", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        RequestAuthorization.clear();
        philter.shutdown();
    }

    @Test
    void redactsTextAndReturnsContentAndDocumentId() throws Exception {
        philter.enqueue(new MockResponse().setBody("His SSN was ***.").setHeader("x-document-id", "doc-1"));

        final FilterResponse response = engine.redactText("His SSN was 123-45-6789.", "default", null);

        assertEquals("His SSN was ***.", response.getFilteredText());
        assertEquals("doc-1", response.getDocumentId());

        final RecordedRequest request = philter.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().startsWith("/api/filter"));
        assertEquals("default", request.getRequestUrl().queryParameter("p"));
    }

    @Test
    void forwardsConfiguredKeyWhenNoPerRequestAuthorization() throws Exception {
        philter.enqueue(new MockResponse().setBody("x"));
        engine.redactText("t", "default", null);
        assertEquals("sk_configuredkey", philter.takeRequest().getHeader("Authorization"));
    }

    @Test
    void perRequestAuthorizationOverridesConfiguredKey() throws Exception {
        philter.enqueue(new MockResponse().setBody("x"));
        RequestAuthorization.set("sk_callerkey");
        engine.redactText("t", "default", null);
        assertEquals("sk_callerkey", philter.takeRequest().getHeader("Authorization"));
    }

    @Test
    void redactsBinaryFile(@TempDir final Path tmp) throws Exception {
        final Path file = tmp.resolve("report.pdf");
        Files.write(file, new byte[]{1, 2, 3});
        philter.enqueue(new MockResponse().setBody(new Buffer().write(new byte[]{9, 9, 9})));

        final BinaryFilterResponse response = engine.redact(file.toFile(), "office", null);

        assertArrayEquals(new byte[]{9, 9, 9}, response.getContent());
        final RecordedRequest request = philter.takeRequest();
        assertTrue(request.getPath().startsWith("/api/filter"));
        assertEquals("report.pdf", request.getRequestUrl().queryParameter("filename"));
        assertEquals("office", request.getRequestUrl().queryParameter("p"));
    }

}
