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

import ai.philterd.router.model.AttributeSources;
import ai.philterd.router.model.FileAttributes;
import ai.philterd.router.model.RoutingDecision;
import ai.philterd.router.routing.Router;
import ai.philterd.router.util.Hashing;

import java.io.IOException;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared routing step for the controllers: write bytes to a temp file, route, apply any policy override. */
public class ApiRouting {

    private final Router router;
    private final AttributeSources sources;

    public ApiRouting(final Router router, final AttributeSources sources) {
        this.router = router;
        this.sources = sources;
    }

    /** The routing result plus the temp file (which the caller must delete when done). */
    public record Result(Path tempFile, FileAttributes attributes, RoutingDecision decision, String hash) {
    }

    public Result evaluate(final byte[] content, final String filename, final String directoryHint,
                           final String policyOverride, final Map<String, String> classificationHints)
            throws IOException {

        final Path tempFile = Files.createTempFile("philter-router-", ".upload");
        Files.write(tempFile, content);

        final FileAttributes attributes = new FileAttributes(tempFile.toFile(), filename, directoryHint,
                sources, classificationHints);
        RoutingDecision decision = router.route(attributes);
        if (!decision.rejected() && policyOverride != null && !policyOverride.isBlank()) {
            decision = new RoutingDecision(decision.matchedRoute(), decision.engine(), policyOverride,
                    decision.isDefault(), false);
        }

        return new Result(tempFile, attributes, decision, Hashing.sha256(content));
    }

    public static void deleteQuietly(final Path tempFile) {
        try {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        } catch (final IOException ignored) {
            // Best effort; the OS temp directory is cleaned up eventually.
        }
    }

}
