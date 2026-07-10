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
import ai.philterd.router.config.EngineConfig;
import ai.philterd.router.config.Outcome;
import ai.philterd.router.config.Route;
import ai.philterd.router.config.RouteMatch;
import ai.philterd.router.config.RouterConfig;
import ai.philterd.router.model.AttributeSources;
import ai.philterd.router.model.FileAttributes;
import ai.philterd.router.model.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    /** Configurable, counting attribute source so the routing tiers can be verified without any I/O. */
    private static final class FakeSources implements AttributeSources {
        String contentType;
        String text = "some text";
        Optional<String> language = Optional.of("eng");
        Map<String, String> labels = Map.of();
        int languageCalls;
        int classifyCalls;

        @Override public String detectContentType(File file, String filename) {
            return contentType;
        }
        @Override public String extractText(File file) {
            return text;
        }
        @Override public Optional<String> detectLanguage(String t) {
            languageCalls++;
            return language;
        }
        @Override public Optional<String> classify(String classifierName, String t) {
            classifyCalls++;
            return Optional.ofNullable(labels.get(classifierName));
        }
    }

    private static RouterConfig config(final List<Route> routes) {
        final RouterConfig c = new RouterConfig();
        final EngineConfig e = new EngineConfig();
        e.url = "http://localhost:8080";
        c.engines = Map.of("philter1", e, "philter2", e);
        final Outcome def = new Outcome();
        def.engine = "philter1";
        def.policy = "default";
        c.defaultOutcome = def;
        c.routes = routes;
        return c;
    }

    private static Route route(final String name, final RouteMatch match, final List<String> languages,
                               final String engine, final String policy) {
        final Route r = new Route();
        r.name = name;
        r.match = match;
        r.languages = languages;
        r.engine = engine;
        r.policy = policy;
        return r;
    }

    private static RouteMatch ext(final String... extensions) {
        final RouteMatch m = new RouteMatch();
        m.extensions = List.of(extensions);
        return m;
    }

    private static FileAttributes attrs(final String path, final FakeSources sources) {
        return new FileAttributes(new File(path), sources);
    }

    @Test
    void routesByExtension() {
        final Router router = new Router(config(List.of(
                route("office", ext(".docx"), List.of("any"), "philter2", "office"))));
        final RoutingDecision d = router.route(attrs("/in/report.docx", new FakeSources()));
        assertEquals("office", d.matchedRoute());
        assertEquals("philter2", d.engine());
        assertEquals("office", d.policy());
        assertFalse(d.isDefault());
    }

    @Test
    void fallsToDefaultWhenNoRouteMatches() {
        final Router router = new Router(config(List.of(
                route("office", ext(".docx"), List.of("any"), "philter2", "office"))));
        final RoutingDecision d = router.route(attrs("/in/photo.png", new FakeSources()));
        assertTrue(d.isDefault());
        assertEquals("philter1", d.engine());
        assertEquals("default", d.policy());
    }

    @Test
    void defaultLanguageIsEnglishOnly() {
        final FakeSources s = new FakeSources();
        s.language = Optional.of("spa"); // Spanish document
        // Route omits languages -> defaults to [eng], so a Spanish document must not match.
        final Router router = new Router(config(List.of(
                route("docs", ext(".docx"), null, "philter2", "office"))));
        final RoutingDecision d = router.route(attrs("/in/informe.docx", s));
        assertTrue(d.isDefault());
    }

    @Test
    void explicitLanguageMatches() {
        final FakeSources s = new FakeSources();
        s.language = Optional.of("spa");
        final Router router = new Router(config(List.of(
                route("docs", ext(".docx"), List.of("eng", "spa"), "philter2", "office"))));
        final RoutingDecision d = router.route(attrs("/in/informe.docx", s));
        assertEquals("docs", d.matchedRoute());
    }

    @Test
    void anyLanguageBypassesTheGateAndSkipsDetection() {
        final FakeSources s = new FakeSources();
        final Router router = new Router(config(List.of(
                route("docs", ext(".docx"), List.of("any"), "philter2", "office"))));
        router.route(attrs("/in/report.docx", s));
        assertEquals(0, s.languageCalls, "language detection should be skipped when languages is [any]");
    }

    @Test
    void undetectedLanguageFallsToDefault() {
        final FakeSources s = new FakeSources();
        s.language = Optional.empty(); // low confidence / no text
        final Router router = new Router(config(List.of(
                route("docs", ext(".docx"), List.of("eng"), "philter2", "office"))));
        assertTrue(router.route(attrs("/in/report.docx", s)).isDefault());
    }

    @Test
    void classificationMatches() {
        final FakeSources s = new FakeSources();
        s.labels = Map.of("doc-type", "medical");
        final RouteMatch m = new RouteMatch();
        m.classification = classification("doc-type", "medical");
        final Router router = new Router(config(List.of(
                route("med", m, List.of("any"), "philter1", "hipaa"))));
        assertEquals("med", router.route(attrs("/in/chart.pdf", s)).matchedRoute());
    }

    @Test
    void presetClassificationSkipsTheClassifier() {
        final FakeSources s = new FakeSources(); // no labels; running the classifier would not match
        final RouteMatch m = new RouteMatch();
        m.classification = classification("doc-type", "medical");
        final Router router = new Router(config(List.of(
                route("med", m, List.of("any"), "philter1", "hipaa"))));
        final FileAttributes attrs = new FileAttributes(new File("/in/chart.pdf"), null, null, s,
                Map.of("doc-type", "medical"));
        assertEquals("med", router.route(attrs).matchedRoute());
        assertEquals(0, s.classifyCalls, "a caller-supplied classification must not run the classifier");
    }

    @Test
    void classifierNotCalledWhenCheapMatchFails() {
        final FakeSources s = new FakeSources();
        s.labels = Map.of("doc-type", "medical");
        final RouteMatch m = ext(".docx");
        m.classification = classification("doc-type", "medical");
        // File is a .pdf, so the extension condition fails before the classifier is consulted.
        final Router router = new Router(config(List.of(
                route("med", m, List.of("any"), "philter1", "hipaa"))));
        router.route(attrs("/in/chart.pdf", s));
        assertEquals(0, s.classifyCalls, "classifier must not run when a cheaper condition already failed");
    }

    @Test
    void classifierNotCalledWhenLanguageFails() {
        final FakeSources s = new FakeSources();
        s.language = Optional.of("spa");
        s.labels = Map.of("doc-type", "medical");
        final RouteMatch m = new RouteMatch();
        m.classification = classification("doc-type", "medical");
        // languages defaults to [eng]; a Spanish doc fails the language gate before classification.
        final Router router = new Router(config(List.of(
                route("med", m, null, "philter1", "hipaa"))));
        router.route(attrs("/in/historia.pdf", s));
        assertEquals(0, s.classifyCalls, "classifier must not run when the language gate already failed");
    }

    @Test
    void rejectDefaultProducesRejectedDecisionForUnmatchedFiles() {
        final RouterConfig c = config(List.of(
                route("office", ext(".docx"), List.of("any"), "philter2", "office")));
        c.defaultOutcome = new Outcome();
        c.defaultOutcome.action = "reject";
        final Router router = new Router(c);

        final RoutingDecision unmatched = router.route(attrs("/in/photo.png", new FakeSources()));
        assertTrue(unmatched.isDefault());
        assertTrue(unmatched.rejected());
        assertNull(unmatched.engine());

        // A matching route is unaffected by a rejecting default.
        final RoutingDecision matched = router.route(attrs("/in/report.docx", new FakeSources()));
        assertEquals("office", matched.matchedRoute());
        assertFalse(matched.rejected());
    }

    @Test
    void directoryPrefixMatches() {
        final RouteMatch m = new RouteMatch();
        m.directories = List.of("/mnt/shares/legal");
        final Router router = new Router(config(List.of(
                route("legal", m, List.of("any"), "philter1", "legal-strict"))));
        assertEquals("legal", router.route(attrs("/mnt/shares/legal/case.pdf", new FakeSources())).matchedRoute());
        assertTrue(router.route(attrs("/mnt/shares/hr/case.pdf", new FakeSources())).isDefault());
    }

    private static ClassificationMatch classification(final String classifier, final String label) {
        final ClassificationMatch cm = new ClassificationMatch();
        cm.classifier = classifier;
        cm.label = label;
        return cm;
    }

}
