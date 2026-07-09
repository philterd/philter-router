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

import ai.philterd.router.config.ClassificationMatch;
import ai.philterd.router.config.ClassifierConfig;
import ai.philterd.router.config.EngineConfig;
import ai.philterd.router.config.Outcome;
import ai.philterd.router.config.Route;
import ai.philterd.router.config.RouteMatch;
import ai.philterd.router.config.RouterConfig;
import ai.philterd.router.config.ServerConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Integration test: the running HTTP API against a stubbed Philter engine. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIT {

    private static MockWebServer philter;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void philterUrl(final DynamicPropertyRegistry registry) throws IOException {
        philter = new MockWebServer();
        philter.start();
        registry.add("philter.url", () -> philter.url("/").toString());
    }

    @AfterAll
    static void stop() throws IOException {
        philter.shutdown();
    }

    /** Provides the router configuration the app normally loads from YAML, pointed at the stub. */
    @TestConfiguration
    static class TestBeans {
        @Bean
        RouterConfig routerConfig(@Value("${philter.url}") final String url) {
            final RouterConfig config = new RouterConfig();
            final ServerConfig server = new ServerConfig();
            server.enabled = true;
            config.server = server;

            final EngineConfig engine = new EngineConfig();
            engine.url = url;
            config.engines = Map.of("philter1", engine);

            final ClassifierConfig classifier = new ClassifierConfig();
            classifier.endpoint = "http://localhost:1"; // unreachable; only the preset path is exercised
            classifier.model = "test";
            classifier.prompt = "{{text}}";
            classifier.timeoutMs = 300;
            classifier.labels = List.of("medical", "general");
            config.classifiers = Map.of("doc-type", classifier);

            final Route medical = new Route();
            medical.name = "medical";
            final RouteMatch match = new RouteMatch();
            final ClassificationMatch cm = new ClassificationMatch();
            cm.classifier = "doc-type";
            cm.label = "medical";
            match.classification = cm;
            medical.match = match;
            medical.languages = List.of("any");
            medical.engine = "philter1";
            medical.policy = "hipaa";
            config.routes = List.of(medical);

            final Outcome def = new Outcome();
            def.engine = "philter1";
            def.policy = "default";
            config.defaultOutcome = def;
            return config;
        }
    }

    private HttpResponse<String> postFilter(final String query, final String body,
                                            final Map<String, String> headers) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/filter" + query))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void filtersTextAndReturnsRedactedBody() throws Exception {
        philter.enqueue(new MockResponse().setBody("His SSN was ***."));

        // No classification hint and the classifier is unreachable, so this routes to the default policy.
        final HttpResponse<String> response = postFilter("", "His SSN was 123-45-6789.", Map.of());

        assertEquals(200, response.statusCode());
        assertEquals("His SSN was ***.", response.body());
        assertEquals("default", response.headers().firstValue("X-Philter-Policy").orElse(null));
        assertEquals("default", philter.takeRequest().getRequestUrl().queryParameter("p"));
    }

    @Test
    void preComputedClassificationRoutesToItsPolicy() throws Exception {
        philter.enqueue(new MockResponse().setBody("redacted"));

        final HttpResponse<String> response =
                postFilter("", "clinical note", Map.of("X-Classification", "doc-type=medical"));

        assertEquals(200, response.statusCode());
        // The preset label routes to the medical route without calling the (unreachable) classifier.
        assertEquals("hipaa", philter.takeRequest().getRequestUrl().queryParameter("p"));
    }

    @Test
    void forwardsAuthorizationHeaderToPhilter() throws Exception {
        philter.enqueue(new MockResponse().setBody("x"));

        postFilter("", "text", Map.of("Authorization", "sk_callerkey"));

        final RecordedRequest request = philter.takeRequest();
        assertEquals("sk_callerkey", request.getHeader("Authorization"));
    }

}
