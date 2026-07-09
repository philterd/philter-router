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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks processed files by content hash so a file is redacted exactly once even when the watcher
 * re-observes it (duplicate events, a reconcile scan, or a restart within the process lifetime).
 *
 * <p>This in-memory ledger is per-process. A durable, restart-surviving ledger is a follow-up.
 */
public class ProcessedLedger {

    private final Set<String> hashes = ConcurrentHashMap.newKeySet();

    /** Records the hash; returns true if it was newly added (i.e. not already processed). */
    public boolean markProcessed(final String hash) {
        return hashes.add(hash);
    }

    public boolean isProcessed(final String hash) {
        return hashes.contains(hash);
    }

}
