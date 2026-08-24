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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void reportsTheBuildVersion() {
        final Properties properties = new Properties();
        properties.setProperty("version", "1.2.3");

        final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("buildProperties", new BuildProperties(properties));

        final HealthController controller = new HealthController(beans.getBeanProvider(BuildProperties.class));

        assertEquals("UP", controller.health().get("status"));
        assertEquals("1.2.3", controller.health().get("applicationVersion"));
    }

    /** Without build-info.properties there is no BuildProperties bean, and health still answers. */
    @Test
    void reportsUnknownWithoutBuildInfo() {
        final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();

        final HealthController controller = new HealthController(beans.getBeanProvider(BuildProperties.class));

        assertEquals("UP", controller.health().get("status"));
        assertEquals("unknown", controller.health().get("applicationVersion"));
    }

}
