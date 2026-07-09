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
package ai.philterd.router;

import ai.philterd.router.config.ConfigLoader;
import ai.philterd.router.config.RouterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point. Config is loaded and validated (fail-closed) before the context starts and decides the
 * shape: the web server runs only when {@code server} is set, watchers only when {@code watch.locations}
 * is set. The loaded {@link RouterConfig} is registered as a bean.
 */
@SpringBootApplication
public class PhilterRouterApplication {

    public static void main(final String[] args) {

        final Path configPath = Path.of(resolveConfigPath(args));
        final RouterConfig config = new ConfigLoader().load(configPath);

        final boolean apiEnabled = config.server != null && config.server.enabled;

        final SpringApplication app = new SpringApplication(PhilterRouterApplication.class);
        app.setWebApplicationType(apiEnabled ? WebApplicationType.SERVLET : WebApplicationType.NONE);

        final Map<String, Object> properties = new HashMap<>();
        if (apiEnabled) {
            properties.put("server.port", config.server.port);
        }
        app.setDefaultProperties(properties);

        // Make the pre-loaded, validated configuration available to the bean wiring.
        app.addInitializers(context -> {
            if (context instanceof GenericApplicationContext generic) {
                generic.registerBean(RouterConfig.class, () -> config);
            }
        });

        app.run(args);
    }

    private static String resolveConfigPath(final String[] args) {
        if (args.length > 0 && !args[0].startsWith("--")) {
            return args[0];
        }
        return System.getProperty("router.config", "router.yaml");
    }

}
