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

import java.util.List;

/** One routing rule: a match plus a language gate, producing an engine + policy outcome. */
public class Route {

    public String name;
    public RouteMatch match = new RouteMatch();

    /**
     * Allowed ISO 639-3 language codes. When null/empty this defaults to {@code [eng]}
     * (see {@link #effectiveLanguages()}). Use {@code any} to accept all languages.
     */
    public List<String> languages;

    public String engine;
    public String policy;

    /** The languages to apply, defaulting to English when unset. */
    public List<String> effectiveLanguages() {
        return (languages == null || languages.isEmpty()) ? List.of("eng") : languages;
    }

}
