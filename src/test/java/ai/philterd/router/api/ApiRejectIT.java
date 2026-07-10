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
package ai.philterd.router.api;

import ai.philterd.router.config.EngineConfig;
import ai.philterd.router.config.Outcome;
import ai.philterd.router.config.RouterConfig;
import ai.philterd.router.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Integration test: an unmatched document is rejected with 422 when the default is {@code action: reject}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiRejectIT {

    @LocalServerPort
    private int port;

    /** No routes and a rejecting default, so every document is unmatched and refused. */
    @TestConfiguration
    static class TestBeans {
        @Bean
        RouterConfig routerConfig() {
            final RouterConfig config = new RouterConfig();
            final ServerConfig server = new ServerConfig();
            server.enabled = true;
            config.server = server;

            final EngineConfig engine = new EngineConfig();
            engine.url = "http://localhost:1"; // never called
            config.engines = Map.of("philter1", engine);
            config.routes = List.of();

            final Outcome def = new Outcome();
            def.action = "reject";
            config.defaultOutcome = def;
            return config;
        }
    }

    @Test
    void unmatchedDocumentIsRejectedWith422() throws Exception {
        final HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/filter"))
                        .POST(HttpRequest.BodyPublishers.ofString("His SSN is 123-45-6789.")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(422, response.statusCode());
    }

}
