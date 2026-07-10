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
package ai.philterd.router.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tmp;

    private RouterConfig load(final String yaml) throws IOException {
        final Path file = tmp.resolve("router.yaml");
        Files.writeString(file, yaml);
        return new ConfigLoader().load(file);
    }

    private static final String VALID = """
            watch:
              locations:
                - path: "/in"
                  output: "/out"
                  done: "/done"
                  error: "/error"
            engines:
              philter1: { url: "http://localhost:8080" }
            classifiers:
              doc-type:
                endpoint: "http://localhost:11434"
                model: llama3.1
                labels: [medical, general]
                prompt: "classify {{text}}"
            routes:
              - name: med
                match: { classification: { classifier: doc-type, label: medical } }
                languages: [eng]
                engine: philter1
                policy: hipaa
            default:
              engine: philter1
              policy: default
            """;

    @Test
    void loadsAValidConfig() throws IOException {
        final RouterConfig c = load(VALID);
        assertEquals(1, c.routes.size());
        assertEquals("philter1", c.defaultOutcome.engine);
        assertEquals(WatchLocation.Mode.poll, c.watch.locations.get(0).mode);
        assertEquals(List.of("eng"), c.routes.get(0).effectiveLanguages());
    }

    @Test
    void missingDefaultIsRejected() {
        final String yaml = VALID.replace("""
                default:
                  engine: philter1
                  policy: default
                """, "");
        final ConfigException ex = assertThrows(ConfigException.class, () -> load(yaml));
        assertTrue(ex.getMessage().toLowerCase().contains("default"));
    }

    @Test
    void routeReferencingUnknownEngineIsRejected() {
        final String yaml = """
                watch:
                  locations:
                    - path: "/in"
                      output: "/out"
                      done: "/done"
                      error: "/error"
                engines:
                  philter1: { url: "http://localhost:8080" }
                routes:
                  - name: bad
                    match: { extensions: [".pdf"] }
                    engine: nope
                    policy: p
                default:
                  engine: philter1
                  policy: default
                """;
        assertThrows(ConfigException.class, () -> load(yaml));
    }

    @Test
    void classificationLabelNotInClassifierIsRejected() {
        final String yaml = VALID.replace("label: medical", "label: nonexistent");
        final ConfigException ex = assertThrows(ConfigException.class, () -> load(yaml));
        assertTrue(ex.getMessage().toLowerCase().contains("label"));
    }

    @Test
    void serverOnlyConfigIsValidWithoutWatch() throws IOException {
        final String yaml = """
                server:
                  enabled: true
                  port: 8080
                engines:
                  philter1: { url: "http://localhost:8080" }
                default:
                  engine: philter1
                  policy: default
                """;
        final RouterConfig c = load(yaml);
        assertTrue(c.server.enabled);
        assertEquals(8080, c.server.port);
    }

    @Test
    void configWithNeitherServerNorWatchIsRejected() {
        final String yaml = """
                engines:
                  philter1: { url: "http://localhost:8080" }
                default:
                  engine: philter1
                  policy: default
                """;
        assertThrows(ConfigException.class, () -> load(yaml));
    }

    @Test
    void rejectDefaultIsValid() throws IOException {
        final String yaml = VALID.replace("""
                default:
                  engine: philter1
                  policy: default
                """, """
                default:
                  action: reject
                """);
        final RouterConfig c = load(yaml);
        assertTrue(c.defaultOutcome.isReject());
    }

    @Test
    void rejectDefaultWithEngineOrPolicyIsRejected() {
        final String yaml = VALID.replace("""
                default:
                  engine: philter1
                  policy: default
                """, """
                default:
                  action: reject
                  engine: philter1
                  policy: default
                """);
        assertThrows(ConfigException.class, () -> load(yaml));
    }

    @Test
    void unknownDefaultActionIsRejected() {
        final String yaml = VALID.replace("""
                default:
                  engine: philter1
                  policy: default
                """, """
                default:
                  action: bogus
                  engine: philter1
                  policy: default
                """);
        assertThrows(ConfigException.class, () -> load(yaml));
    }

    @Test
    void defaultReferencingUnknownEngineIsRejected() {
        final String yaml = VALID.replace("""
                default:
                  engine: philter1
                  policy: default
                """, """
                default:
                  engine: ghost
                  policy: default
                """);
        assertThrows(ConfigException.class, () -> load(yaml));
    }

}
