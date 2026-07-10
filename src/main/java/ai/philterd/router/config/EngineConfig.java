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

/** A named Philter engine target. */
public class EngineConfig {

    public String url;

    /** Optional API key. Prefer an environment reference over an inline secret. */
    public String apiKey;

    /** Optional Philter context passed on filter calls. */
    public String context = "";

    /** TCP connect timeout. */
    public long connectTimeoutMs = 10_000;

    /** Inactivity timeout while uploading the file body. */
    public long writeTimeoutMs = 60_000;

    /** Inactivity timeout while awaiting Philter's response. Size to the slowest routed document. */
    public long readTimeoutMs = 120_000;

    /** Hard end-to-end ceiling for the whole call. 0 disables it. */
    public long callTimeoutMs = 0;

    /** Path to a PEM certificate (or CA) to trust for a self-signed Philter, keeping verification on. */
    public String caCertPath;

    /** Disable TLS verification entirely. Trusted networks / testing only; prefer caCertPath. */
    public boolean insecureSkipVerify = false;

}
