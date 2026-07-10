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
package ai.philterd.router.model;

/**
 * The result of routing a file: which route matched (or the default), and the engine and policy to
 * apply. {@code matchedRoute} is {@code "default"} when no route matched. When {@code rejected} is true
 * the document matched no route and the default is {@code action: reject}, so there is no engine/policy.
 */
public record RoutingDecision(String matchedRoute, String engine, String policy, boolean isDefault,
                              boolean rejected) {

    public static final String DEFAULT = "default";

    public static RoutingDecision ofRoute(final String routeName, final String engine, final String policy) {
        return new RoutingDecision(routeName, engine, policy, false, false);
    }

    public static RoutingDecision ofDefault(final String engine, final String policy) {
        return new RoutingDecision(DEFAULT, engine, policy, true, false);
    }

    public static RoutingDecision rejectedByDefault() {
        return new RoutingDecision(DEFAULT, null, null, true, true);
    }

}
