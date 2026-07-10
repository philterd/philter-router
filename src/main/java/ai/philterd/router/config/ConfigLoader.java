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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Loads and validates the router YAML. Fail-closed: any problem throws {@link ConfigException}. */
public final class ConfigLoader {

    private final ObjectMapper mapper;

    public ConfigLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public RouterConfig load(final Path path) {

        final RouterConfig config;
        try {
            config = mapper.readValue(Files.readString(path), RouterConfig.class);
        } catch (final IOException e) {
            throw new ConfigException("Could not read configuration from " + path + ": " + e.getMessage(), e);
        }

        if (config == null) {
            throw new ConfigException("Configuration at " + path + " is empty.");
        }

        validate(config);
        return config;
    }

    /** Validates a fully-parsed configuration. Public so tests can validate in-memory configs. */
    public void validate(final RouterConfig config) {

        final Map<String, EngineConfig> engines = config.engines;
        if (engines == null || engines.isEmpty()) {
            throw new ConfigException("At least one engine must be defined under 'engines'.");
        }
        engines.forEach((name, engine) -> {
            if (engine == null || isBlank(engine.url)) {
                throw new ConfigException("Engine '" + name + "' must define a 'url'.");
            }
        });

        final Map<String, ClassifierConfig> classifiers = config.classifiers;
        if (classifiers != null) {
            classifiers.forEach((name, c) -> {
                if (c == null || isBlank(c.endpoint) || isBlank(c.model) || isBlank(c.prompt)
                        || c.labels == null || c.labels.isEmpty()) {
                    throw new ConfigException("Classifier '" + name
                            + "' must define endpoint, model, prompt, and a non-empty labels list.");
                }
            });
        }

        // The default catch-all is mandatory (fail-closed): either redact with a real engine, or reject.
        final Outcome def = config.defaultOutcome;
        if (def == null) {
            throw new ConfigException("A 'default' block is required: an 'engine' and 'policy', or 'action: reject'.");
        }
        if (def.action != null && !def.action.equalsIgnoreCase("redact") && !def.action.equalsIgnoreCase("reject")) {
            throw new ConfigException("Default 'action' must be 'redact' or 'reject'.");
        }
        if (def.isReject()) {
            if (!isBlank(def.engine) || !isBlank(def.policy)) {
                throw new ConfigException("A rejecting default ('action: reject') must not set an engine or policy.");
            }
        } else {
            if (isBlank(def.engine) || isBlank(def.policy)) {
                throw new ConfigException("A 'default' block with an 'engine' and 'policy' is required "
                        + "(or 'action: reject').");
            }
            if (!engines.containsKey(def.engine)) {
                throw new ConfigException("Default engine '" + def.engine + "' is not defined under 'engines'.");
            }
        }

        final List<Route> routes = config.routes == null ? List.of() : config.routes;
        for (final Route route : routes) {
            final String label = route.name == null ? "(unnamed)" : route.name;
            if (isBlank(route.engine) || !engines.containsKey(route.engine)) {
                throw new ConfigException("Route '" + label + "' references unknown engine '" + route.engine + "'.");
            }
            if (isBlank(route.policy)) {
                throw new ConfigException("Route '" + label + "' must define a 'policy'.");
            }
            final ClassificationMatch cm = route.match == null ? null : route.match.classification;
            if (cm != null) {
                if (isBlank(cm.classifier) || classifiers == null || !classifiers.containsKey(cm.classifier)) {
                    throw new ConfigException("Route '" + label + "' references unknown classifier '"
                            + cm.classifier + "'.");
                }
                if (isBlank(cm.label) || !classifiers.get(cm.classifier).labels.contains(cm.label)) {
                    throw new ConfigException("Route '" + label + "' classification label '" + cm.label
                            + "' is not one of classifier '" + cm.classifier + "' labels.");
                }
            }
        }

        final boolean hasServer = config.server != null && config.server.enabled;
        final boolean hasWatch = config.watch != null && config.watch.locations != null
                && !config.watch.locations.isEmpty();

        if (!hasServer && !hasWatch) {
            throw new ConfigException("Enable the HTTP API ('server.enabled: true') or define "
                    + "'watch.locations' (or both). At least one entry point is required.");
        }

        if (hasServer && config.server.port <= 0) {
            throw new ConfigException("server.port must be a positive port number.");
        }

        if (hasWatch) {
            for (final WatchLocation loc : config.watch.locations) {
                if (isBlank(loc.path) || isBlank(loc.output) || isBlank(loc.done) || isBlank(loc.error)) {
                    throw new ConfigException("Each watch location must define path, output, done, and error.");
                }
            }
        }
    }

    private static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }

}
