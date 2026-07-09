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

import ai.philterd.router.config.EngineConfig;

import java.util.HashMap;
import java.util.Map;

/** Holds the configured Philter engines by name. */
public class EngineRegistry {

    private final Map<String, PhilterEngine> engines = new HashMap<>();

    public EngineRegistry(final Map<String, EngineConfig> configs) {
        configs.forEach((name, config) -> engines.put(name, new PhilterEngine(name, config)));
    }

    public PhilterEngine get(final String name) {
        final PhilterEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException("No engine named '" + name + "' is configured.");
        }
        return engine;
    }

}
