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
package ai.philterd.router.classify;

import ai.philterd.router.config.ClassifierConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Runs a local LLM classifier (Ollama) returning one label from its fixed set. On timeout, failure, or
 * an unrecognized label it returns empty (the router then applies the default). Prompt and response are
 * never logged.
 */
public class Classifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(Classifier.class);
    private static final String TOKEN = "{{text}}";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Classifies the text with the given classifier, returning its label or empty. */
    public Optional<String> classify(final ClassifierConfig config, final String text) {

        if (config == null || text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            final String prompt = config.prompt.replace(TOKEN, text);

            final ObjectNode body = mapper.createObjectNode();
            body.put("model", config.model);
            body.put("prompt", prompt);
            body.put("stream", false);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint.replaceAll("/+$", "") + "/api/generate"))
                    .timeout(Duration.ofMillis(config.timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOGGER.warn("Classifier '{}' returned HTTP {}", config.model, response.statusCode());
                return Optional.empty();
            }

            final JsonNode root = mapper.readTree(response.body());
            final String raw = root.path("response").asText("").trim();
            return matchLabel(config, raw);

        } catch (final Exception e) {
            // Never propagate: a classifier failure must fall back to the safe default, not abort routing.
            LOGGER.warn("Classifier '{}' failed: {}", config.model, e.getMessage());
            return Optional.empty();
        }
    }

    /** Accepts the response only if it corresponds to exactly one configured label. */
    private static Optional<String> matchLabel(final ClassifierConfig config, final String raw) {
        for (final String label : config.labels) {
            if (label.equalsIgnoreCase(raw)) {
                return Optional.of(label);
            }
        }
        // Tolerate a model that answers in a sentence: accept iff exactly one label appears.
        String found = null;
        final String lower = raw.toLowerCase();
        for (final String label : config.labels) {
            if (lower.contains(label.toLowerCase())) {
                if (found != null) {
                    return Optional.empty();
                }
                found = label;
            }
        }
        return Optional.ofNullable(found);
    }

}
