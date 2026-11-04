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

import ai.philterd.philter.PhilterClient;
import ai.philterd.philter.model.BinaryFilterResponse;
import ai.philterd.philter.model.FilterResponse;
import ai.philterd.router.config.EngineConfig;

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/** A named Philter engine the router forwards files to, wrapping the Philter Java SDK client. */
public class PhilterEngine {

    private final String name;
    private final String context;
    private final PhilterClient client;

    public PhilterEngine(final String name, final EngineConfig config) {
        this.name = name;
        this.context = config.context == null ? "" : config.context;
        final String configuredApiKey = config.apiKey;

        // Redirects stay off so a document and the caller's Authorization never reach a host other than
        // the configured engine. HTTP/1.1 and the proxy selector match the SDK's own client.
        final HttpClient.Builder http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        if (config.connectTimeoutMs > 0) {
            http.connectTimeout(Duration.ofMillis(config.connectTimeoutMs));
        }
        final ProxySelector proxySelector = ProxySelector.getDefault();
        if (proxySelector != null) {
            http.proxy(proxySelector);
        }

        TlsSupport.apply(http, config, name);

        // Set Authorization from the caller's request when present, else the configured key. This lets
        // the router forward a caller's Authorization header to Philter per request.
        this.client = new PhilterClient.PhilterClientBuilder()
                .withEndpoint(config.url)
                .withHttpClientBuilder(new EngineHttpClient.Builder(http,
                        () -> resolveAuthorization(RequestAuthorization.get(), configuredApiKey),
                        requestTimeout(config)))
                .build();
    }

    /** The deadline for a whole request: upload plus response, capped by callTimeoutMs when set. */
    static Duration requestTimeout(final EngineConfig config) {
        final long budget = config.writeTimeoutMs + config.readTimeoutMs;
        final long timeout;
        if (config.callTimeoutMs > 0) {
            timeout = budget > 0 ? Math.min(config.callTimeoutMs, budget) : config.callTimeoutMs;
        } else {
            timeout = budget;
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("An engine needs a positive writeTimeoutMs, readTimeoutMs, "
                    + "or callTimeoutMs.");
        }
        return Duration.ofMillis(timeout);
    }

    /** The per-request Authorization if present, otherwise the configured key, otherwise none. */
    static String resolveAuthorization(final String perRequest, final String configured) {
        if (perRequest != null && !perRequest.isBlank()) {
            return perRequest;
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return null;
    }

    public String name() {
        return name;
    }

    /** Sends the file to Philter under the given policy (default context). */
    public BinaryFilterResponse redact(final File file, final String policy) throws IOException {
        return redact(file, policy, null);
    }

    /** Sends the file to Philter under the given policy and context. */
    public BinaryFilterResponse redact(final File file, final String policy, final String context)
            throws IOException {
        return client.filter(effectiveContext(context), policy, file.getName(), file);
    }

    /** Sends text to Philter under the given policy and context. */
    public FilterResponse redactText(final String text, final String policy, final String context)
            throws IOException {
        return client.filter(effectiveContext(context), policy, text);
    }

    private String effectiveContext(final String context) {
        return (context != null && !context.isBlank()) ? context : this.context;
    }

    /** Whether the engine reports healthy. */
    public boolean healthy() {
        try {
            return client.health() != null;
        } catch (final IOException e) {
            return false;
        }
    }

}
