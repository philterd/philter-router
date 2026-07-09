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

import ai.philterd.router.config.WatchLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Watches one location and hands new files to the worker pool. Poll mode scans the directory (required
 * on network shares); notify mode uses OS notifications plus a reconcile scan. An in-flight guard
 * avoids double submission; the ledger avoids reprocessing.
 */
public class FolderWatcher implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(FolderWatcher.class);
    private static final long NOTIFY_RECONCILE_MS = 60_000;

    private final WatchLocation location;
    private final FileProcessor processor;
    private final ExecutorService workers;

    private final Path root;
    private final List<Path> excluded;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private WatchService watchService;
    private Thread notifyThread;

    public FolderWatcher(final WatchLocation location, final FileProcessor processor, final ExecutorService workers) {
        this.location = location;
        this.processor = processor;
        this.workers = workers;
        this.root = Path.of(location.path).toAbsolutePath().normalize();
        this.excluded = List.of(
                Path.of(location.output).toAbsolutePath().normalize(),
                Path.of(location.done).toAbsolutePath().normalize(),
                Path.of(location.error).toAbsolutePath().normalize());
    }

    public void start() throws IOException {
        Files.createDirectories(root);

        if (location.mode == WatchLocation.Mode.notify) {
            startNotify();
            // Reconcile scan is the safety net for dropped/coalesced events.
            scheduler.scheduleWithFixedDelay(this::scanQuietly, NOTIFY_RECONCILE_MS, NOTIFY_RECONCILE_MS, TimeUnit.MILLISECONDS);
            LOGGER.info("Watching {} in notify mode (with a {}ms reconcile scan).", root, NOTIFY_RECONCILE_MS);
        } else {
            scheduler.scheduleWithFixedDelay(this::scanQuietly, 0, location.pollIntervalMs, TimeUnit.MILLISECONDS);
            LOGGER.info("Watching {} in poll mode every {}ms.", root, location.pollIntervalMs);
        }
    }

    private void startNotify() throws IOException {
        this.watchService = root.getFileSystem().newWatchService();
        registerRecursively(root);
        this.notifyThread = new Thread(this::notifyLoop, "watch-notify-" + root.getFileName());
        this.notifyThread.setDaemon(true);
        this.notifyThread.start();
    }

    private void registerRecursively(final Path dir) throws IOException {
        if (isExcluded(dir)) {
            return;
        }
        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        if (location.recursive) {
            try (Stream<Path> children = Files.list(dir)) {
                children.filter(Files::isDirectory).forEach(child -> {
                    try {
                        registerRecursively(child);
                    } catch (final IOException e) {
                        LOGGER.warn("Could not register subdirectory for notifications: {}", e.getMessage());
                    }
                });
            }
        }
    }

    private void notifyLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            final WatchKey key;
            try {
                key = watchService.take();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final Exception e) {
                return;
            }
            final Path dir = (Path) key.watchable();
            key.pollEvents().forEach(event -> {
                final Object ctx = event.context();
                if (ctx instanceof Path relative) {
                    final Path resolved = dir.resolve(relative);
                    if (Files.isDirectory(resolved) && location.recursive) {
                        try {
                            registerRecursively(resolved);
                        } catch (final IOException e) {
                            LOGGER.warn("Could not register a new subdirectory: {}", e.getMessage());
                        }
                    } else if (Files.isRegularFile(resolved)) {
                        submit(resolved);
                    }
                }
            });
            key.reset();
        }
    }

    private void scanQuietly() {
        try {
            scan();
        } catch (final Exception e) {
            LOGGER.warn("Scan of {} failed: {}", root, e.getMessage());
        }
    }

    private void scan() throws IOException {
        final int depth = location.recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(root, depth, FileVisitOption.FOLLOW_LINKS)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(p))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(this::submit);
        }
    }

    private void submit(final Path file) {
        final Path abs = file.toAbsolutePath().normalize();
        if (isExcluded(abs) || abs.getFileName().toString().startsWith(".")) {
            return;
        }
        final String key = abs.toString();
        if (!inFlight.add(key)) {
            return;
        }
        workers.execute(() -> {
            try {
                processor.process(abs, location);
            } finally {
                inFlight.remove(key);
            }
        });
    }

    private boolean isExcluded(final Path path) {
        final Path abs = path.toAbsolutePath().normalize();
        return excluded.stream().anyMatch(abs::startsWith);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        if (notifyThread != null) {
            notifyThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (final IOException e) {
                LOGGER.warn("Error closing watch service: {}", e.getMessage());
            }
        }
    }

}
