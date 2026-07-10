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

import ai.philterd.philter.model.BinaryFilterResponse;
import ai.philterd.philter.model.FilterResponse;
import ai.philterd.router.audit.AuditLogger;
import ai.philterd.router.engine.EngineRegistry;
import ai.philterd.router.engine.RequestAuthorization;
import ai.philterd.router.metrics.RouterMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code POST /api/filter} - redact text or a file, mirroring Philter's synchronous filter. A file is
 * sent when {@code ?filename=} is present (matching the Philter SDK); otherwise the body is text. The
 * policy is auto-selected unless {@code ?p=} overrides it; {@code ?c=} sets the context and
 * {@code X-Source-Directory} a directory hint. The engine is chosen by the router.
 */
@RestController
public class FilterController {

    private static final Logger LOGGER = LogManager.getLogger(FilterController.class);

    private final ApiRouting routing;
    private final EngineRegistry engines;
    private final AuditLogger audit;
    private final RouterMetrics metrics;

    public FilterController(final ApiRouting routing, final EngineRegistry engines, final AuditLogger audit,
                            final RouterMetrics metrics) {
        this.routing = routing;
        this.engines = engines;
        this.audit = audit;
        this.metrics = metrics;
    }

    @PostMapping("/api/filter")
    public ResponseEntity<byte[]> filter(final HttpServletRequest request) throws IOException {

        final byte[] body = request.getInputStream().readAllBytes();
        final Map<String, String> query = HttpQuery.params(request.getQueryString());
        final String policyOverride = query.get("p");
        final String context = query.get("c");
        final String filename = query.get("filename");
        final String directoryHint = request.getHeader("X-Source-Directory");
        final Map<String, String> classificationHints =
                parseClassificationHints(request.getHeader("X-Classification"));
        final boolean isDocument = filename != null && !filename.isBlank();
        // The filename for failure diagnostics on the operational log; the audit trail stays hash-only.
        final String fileLabel = isDocument ? filename : "(text request)";

        final ApiRouting.Result result;
        try {
            result = routing.evaluate(body, filename, directoryHint, policyOverride, classificationHints);
        } catch (final Exception e) {
            LOGGER.error("Failed /api/filter request for '{}': {}", fileLabel, e.getMessage());
            metrics.recordFailed();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Failed to process the request.".getBytes(StandardCharsets.UTF_8));
        }

        if (result.decision().rejected()) {
            // No route matched and the default rejects: refuse the document, do not forward to Philter.
            audit.rejected(result.hash(), result.decision(),
                    result.attributes().computedLanguage(), result.attributes().computedClassifications());
            metrics.recordRejected(result.decision());
            ApiRouting.deleteQuietly(result.tempFile());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No matching route; document rejected.".getBytes(StandardCharsets.UTF_8));
        }

        // Forward the caller's Authorization to Philter for this request, if provided.
        RequestAuthorization.set(request.getHeader("Authorization"));
        try {
            final byte[] content;
            final String documentId;
            final MediaType contentType;
            if (isDocument) {
                final BinaryFilterResponse response = engines.get(result.decision().engine())
                        .redact(result.tempFile().toFile(), result.decision().policy(), context);
                content = response.getContent();
                documentId = response.getDocumentId();
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            } else {
                final FilterResponse response = engines.get(result.decision().engine())
                        .redactText(new String(body, StandardCharsets.UTF_8), result.decision().policy(), context);
                content = response.getFilteredText().getBytes(StandardCharsets.UTF_8);
                documentId = response.getDocumentId();
                contentType = MediaType.TEXT_PLAIN;
            }

            audit.routed(result.hash(), result.decision(),
                    result.attributes().computedLanguage(), result.attributes().computedClassifications());
            metrics.recordRouted(result.decision());

            final ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                    .header("X-Philter-Policy", result.decision().policy())
                    .contentType(contentType);
            if (documentId != null) {
                ok.header("x-document-id", documentId);
            }
            return ok.body(content);

        } catch (final Exception e) {
            LOGGER.error("Redaction engine call failed for '{}': {}", fileLabel, e.getMessage());
            audit.failed(result.hash(), e.getMessage());
            metrics.recordFailed();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Redaction engine call failed.".getBytes(StandardCharsets.UTF_8));
        } finally {
            RequestAuthorization.clear();
            ApiRouting.deleteQuietly(result.tempFile());
        }
    }

    /** Parses an {@code X-Classification} header of comma-separated {@code classifier=label} pairs. */
    private static Map<String, String> parseClassificationHints(final String header) {
        if (header == null || header.isBlank()) {
            return Map.of();
        }
        final Map<String, String> hints = new HashMap<>();
        for (final String pair : header.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                hints.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return hints;
    }

}
