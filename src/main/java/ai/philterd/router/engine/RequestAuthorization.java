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

/**
 * Holds the caller's {@code Authorization} header for the current request thread so the engine can
 * forward it to Philter. The engine call is synchronous on the request thread, so a thread-local is
 * sufficient; the caller must clear it after the call.
 */
public final class RequestAuthorization {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestAuthorization() {
    }

    public static void set(final String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            CURRENT.set(authorization);
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

}
