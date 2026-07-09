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

/** A single watched directory and how it is watched. */
public class WatchLocation {

    /** Watch mechanism: "poll" (default, works on network shares) or "notify" (local filesystems only). */
    public enum Mode { poll, notify }

    public String path;
    public Mode mode = Mode.poll;
    public long pollIntervalMs = 5000;
    public long stableForMs = 2000;
    public boolean recursive = true;

    /** Where redacted output is written. */
    public String output;
    /** Where a source file is moved after successful redaction. */
    public String done;
    /** Where a source file is moved if processing fails. */
    public String error;

}
