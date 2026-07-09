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
package ai.philterd.router.watch;

import ai.philterd.router.config.RouterConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Starts the folder watchers when {@code watch.locations} is configured, and stops them on shutdown.
 * When only the HTTP API is configured this does nothing.
 */
@Component
public class WatchLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LogManager.getLogger(WatchLifecycle.class);

    private final RouterConfig config;
    private final FileProcessor processor;

    private WatchManager watchManager;
    private volatile boolean running;

    public WatchLifecycle(final RouterConfig config, final FileProcessor processor) {
        this.config = config;
        this.processor = processor;
    }

    @Override
    public void start() {
        if (config.watch == null || config.watch.locations == null || config.watch.locations.isEmpty()) {
            return;
        }
        final int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        watchManager = new WatchManager(config, processor, workers);
        try {
            watchManager.start();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not start folder watchers.", e);
        }
        running = true;
        LOGGER.info("Folder watching is running with {} worker threads.", workers);
    }

    @Override
    public void stop() {
        if (watchManager != null) {
            watchManager.close();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}
