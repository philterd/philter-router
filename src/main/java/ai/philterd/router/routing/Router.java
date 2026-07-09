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
package ai.philterd.router.routing;

import ai.philterd.router.config.ClassificationMatch;
import ai.philterd.router.config.Route;
import ai.philterd.router.config.RouteMatch;
import ai.philterd.router.config.RouterConfig;
import ai.philterd.router.model.FileAttributes;
import ai.philterd.router.model.RoutingDecision;

import java.util.List;
import java.util.Locale;

/**
 * Evaluates the ordered routes and returns the first match, else the default. Tiered so expensive work
 * runs only when needed: cheap metadata, then the language gate, then classification.
 */
public class Router {

    private static final String ANY_LANGUAGE = "any";

    private final RouterConfig config;

    public Router(final RouterConfig config) {
        this.config = config;
    }

    public RoutingDecision route(final FileAttributes attrs) {

        for (final Route route : routes()) {
            if (!cheapMatch(route.match, attrs)) {
                continue;
            }
            if (!languagePasses(route, attrs)) {
                continue;
            }
            if (!classificationPasses(route.match, attrs)) {
                continue;
            }
            return RoutingDecision.ofRoute(route.name, route.engine, route.policy);
        }

        return RoutingDecision.ofDefault(config.defaultOutcome.engine, config.defaultOutcome.policy);
    }

    private List<Route> routes() {
        return config.routes == null ? List.of() : config.routes;
    }

    /** Content type, extension, and directory conditions. All present conditions must match (AND). */
    private boolean cheapMatch(final RouteMatch match, final FileAttributes attrs) {

        if (match == null || match.isEmpty()) {
            return true;
        }

        if (isNotEmpty(match.contentTypes)) {
            final String ct = attrs.contentType();
            if (ct == null || match.contentTypes.stream().noneMatch(ct::equalsIgnoreCase)) {
                return false;
            }
        }

        if (isNotEmpty(match.extensions)) {
            final String ext = attrs.extension();
            if (match.extensions.stream().map(Router::normalizeExtension).noneMatch(ext::equals)) {
                return false;
            }
        }

        if (isNotEmpty(match.directories)) {
            final String dir = normalizeDirectory(attrs.directory());
            if (match.directories.stream().map(Router::normalizeDirectory)
                    .noneMatch(d -> dir.equals(d) || dir.startsWith(d + "/"))) {
                return false;
            }
        }

        return true;
    }

    /** The route's {@code languages} gate, AND-ed with the match. Defaults to {@code [eng]}. */
    private boolean languagePasses(final Route route, final FileAttributes attrs) {

        final List<String> allowed = route.effectiveLanguages();
        if (allowed.stream().anyMatch(ANY_LANGUAGE::equalsIgnoreCase)) {
            return true;
        }

        final String detected = attrs.language();
        if (detected == null) {
            // Could not confidently determine the language; do not claim it is allowed.
            return false;
        }
        return allowed.stream().anyMatch(detected::equalsIgnoreCase);
    }

    /** The classifier-label condition, if any. Runs the classifier lazily. */
    private boolean classificationPasses(final RouteMatch match, final FileAttributes attrs) {

        final ClassificationMatch cm = match == null ? null : match.classification;
        if (cm == null) {
            return true;
        }
        final String label = attrs.classification(cm.classifier);
        return cm.label != null && cm.label.equals(label);
    }

    private static boolean isNotEmpty(final List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static String normalizeExtension(final String ext) {
        final String lower = ext.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") ? lower : "." + lower;
    }

    private static String normalizeDirectory(final String dir) {
        if (dir == null) {
            return "";
        }
        String d = dir.replace('\\', '/');
        while (d.length() > 1 && d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

}
