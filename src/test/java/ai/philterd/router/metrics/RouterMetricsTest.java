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
package ai.philterd.router.metrics;

import ai.philterd.router.model.RoutingDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterMetricsTest {

    private double count(final SimpleMeterRegistry registry, final String outcome, final String route) {
        return registry.get("philter.router.documents")
                .tag("outcome", outcome).tag("route", route).counter().count();
    }

    @Test
    void countsEachOutcomeUnderItsOwnTags() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final RouterMetrics metrics = new RouterMetrics(registry);

        metrics.recordRouted(RoutingDecision.ofRoute("office", "philter2", "office"));
        metrics.recordRouted(RoutingDecision.ofRoute("office", "philter2", "office"));
        metrics.recordRouted(RoutingDecision.ofDefault("philter1", "default"));
        metrics.recordRejected(RoutingDecision.rejectedByDefault());
        metrics.recordFailed();

        assertEquals(2.0, count(registry, "routed", "office"));
        assertEquals(1.0, count(registry, "default", "default"));
        assertEquals(1.0, count(registry, "rejected", "default"));
        assertEquals(1.0, count(registry, "failed", "none"));
    }

}
