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
package ai.philterd.router.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code GET /api/health} - liveness, matching Philter's contract. Actuator provides deeper checks. */
@RestController
public class HealthController {

    private final String version;

    /**
     * The version comes from META-INF/build-info.properties, written by the build-info goal of the
     * Spring Boot Maven plugin. Running from classes built outside Maven (an IDE, for example) has no
     * such file and therefore no BuildProperties bean, so the version reports as unknown rather than
     * failing startup.
     */
    public HealthController(final ObjectProvider<BuildProperties> buildProperties) {
        final BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "unknown";
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "applicationVersion", version);
    }

}
