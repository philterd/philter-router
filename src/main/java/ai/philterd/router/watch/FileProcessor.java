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
import ai.philterd.router.config.WatchLocation;
import ai.philterd.router.engine.EngineRegistry;
import ai.philterd.router.model.AttributeSources;
import ai.philterd.router.model.FileAttributes;
import ai.philterd.router.model.RoutingDecision;
import ai.philterd.router.routing.Router;
import ai.philterd.router.util.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Redacts one file: wait until fully written, route, send to Philter, write output, drain to done/error. */
public class FileProcessor {

    private static final Logger LOGGER = LogManager.getLogger(FileProcessor.class);

    private final Router router;
    private final EngineRegistry engines;
    private final AttributeSources sources;
    private final ProcessedLedger ledger;
    private final AuditLogger audit;

    public FileProcessor(final Router router, final EngineRegistry engines, final AttributeSources sources,
                         final ProcessedLedger ledger, final AuditLogger audit) {
        this.router = router;
        this.engines = engines;
        this.sources = sources;
        this.ledger = ledger;
        this.audit = audit;
    }

    public void process(final Path source, final WatchLocation location) {

        if (!awaitStable(source, location.stableForMs)) {
            // Still being written; a later poll or the reconcile scan will pick it up.
            LOGGER.debug("Skipping a file that is not yet stable.");
            return;
        }

        String hash = null;
        try {
            hash = Hashing.sha256(source);

            if (!ledger.markProcessed(hash)) {
                // Already redacted identical content; just drain this copy so it is not re-seen.
                moveTo(source, location.done);
                return;
            }

            final FileAttributes attrs = new FileAttributes(source.toFile(), sources);
            final RoutingDecision decision = router.route(attrs);

            if (decision.rejected()) {
                // No route matched and the default rejects: quarantine to error, never emit output.
                moveTo(source, location.error);
                audit.rejected(hash, decision, attrs.computedLanguage(), attrs.computedClassifications());
                return;
            }

            final byte[] redacted = engines.get(decision.engine()).redact(source.toFile(), decision.policy())
                    .getContent();

            final Path out = dir(location.output).resolve(source.getFileName());
            Files.write(out, redacted);

            moveTo(source, location.done);
            audit.routed(hash, decision, attrs.computedLanguage(), attrs.computedClassifications());

        } catch (final Exception e) {
            LOGGER.error("Failed to process a file; moving it to the error location. Reason: {}", e.getMessage());
            try {
                moveTo(source, location.error);
            } catch (final IOException moveError) {
                LOGGER.error("Could not move a failed file to the error location: {}", moveError.getMessage());
            }
            if (hash != null) {
                audit.failed(hash, e.getMessage());
            }
        }
    }

    /** Waits until the file size is unchanged across a sampling interval, or gives up after a few tries. */
    private boolean awaitStable(final Path source, final long stableForMs) {
        try {
            long previous = -1;
            for (int attempt = 0; attempt < 10; attempt++) {
                if (!Files.exists(source)) {
                    return false;
                }
                final long size = Files.size(source);
                if (size == previous) {
                    return true;
                }
                previous = size;
                Thread.sleep(Math.max(1, stableForMs));
            }
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (final IOException e) {
            return false;
        }
    }

    private void moveTo(final Path source, final String targetDir) throws IOException {
        final Path target = dir(targetDir).resolve(source.getFileName());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path dir(final String path) throws IOException {
        final Path dir = new File(path).toPath();
        Files.createDirectories(dir);
        return dir;
    }

}
