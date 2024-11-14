/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
 * All rights reserved.
 *
 * This source code is licensed under the same terms as the rest of the
 * original project, found in the COPYING file in the root directory.
 */
package demo.webauthn.grs.poller;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A reusable component to run a task periodically using ScheduledExecutorService.
 * Designed to poll external APIs at fixed intervals.
 * <p>
 * Usage example:
 * <pre>
 * GrsApiPoller poller = new GrsApiPoller(() -> {
 *     // Your polling logic here
 *     apiClient.fetchData();
 * });
 * poller.start(30); // every 30 seconds
 * </pre>
 */
@Slf4j
public class GrsApiPoller implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final Runnable pollTask;
    private volatile boolean started = false;

    /**
     * Creates a new GrsApiPoller.
     *
     * @param pollTask The task to execute periodically (e.g. calling an external API)
     */
    public GrsApiPoller(Runnable pollTask) {
        if (pollTask == null) {
            throw new IllegalArgumentException("pollTask cannot be null");
        }
        this.pollTask = pollTask;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "grs-api-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the periodic polling.
     *
     * @param intervalSeconds Interval in seconds between executions
     */
    public void start(int intervalSeconds) {
        if (started) {
            throw new IllegalStateException("Poller has already been started");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be greater than 0");
        }

        scheduler.scheduleWithFixedDelay(
                this::safePoll,
                5,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        started = true;
        log.debug("GrsApiPoller started. Interval: {} seconds", intervalSeconds);
    }

    private void safePoll() {
        try {
            pollTask.run();
        } catch (Exception e) {
            log.debug("Polling failed: {}", e.getMessage());
        }
    }

    /**
     * Gracefully shuts down the poller.
     */
    @Override
    public void close() {
        log.debug("Shutting down GrsApiPoller...");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                log.debug("Forcing shutdown of GrsApiPoller...");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.debug("GrsApiPoller stopped.");
    }

    /**
     * Checks if the poller has been started.
     */
    public boolean isStarted() {
        return started;
    }
}