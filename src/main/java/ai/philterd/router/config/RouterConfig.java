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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Root of the router YAML configuration. */
public class RouterConfig {

    public WatchConfig watch;
    public ServerConfig server;
    public Map<String, EngineConfig> engines;
    public Map<String, ClassifierConfig> classifiers;
    public List<Route> routes;

    /** The mandatory catch-all outcome; "default" is a reserved word so it is mapped explicitly. */
    @JsonProperty("default")
    public Outcome defaultOutcome;

}
