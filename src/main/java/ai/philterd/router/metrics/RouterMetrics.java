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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts documents by routing outcome. Exposed via the Prometheus actuator endpoint as
 * {@code philter_router_documents_total{outcome=..., route=...}}. Labels are bounded (a fixed set of
 * outcomes and the configured route names), never the content hash or filename.
 */
public class RouterMetrics {

    private static final String METER = "philter.router.documents";
    private static final String NONE = "none";

    private final MeterRegistry registry;

    public RouterMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    /** A document that was routed to an engine, either by a matching route or the redacting default. */
    public void recordRouted(final RoutingDecision decision) {
        increment(decision.isDefault() ? "default" : "routed", decision.matchedRoute());
    }

    /** A document that matched no route and was refused by a rejecting default. */
    public void recordRejected(final RoutingDecision decision) {
        increment("rejected", decision.matchedRoute());
    }

    /** A document whose processing or engine call failed. */
    public void recordFailed() {
        increment("failed", NONE);
    }

    private void increment(final String outcome, final String route) {
        Counter.builder(METER)
                .description("Documents processed, by routing outcome.")
                .tag("outcome", outcome)
                .tag("route", route == null ? NONE : route)
                .register(registry)
                .increment();
    }

}
