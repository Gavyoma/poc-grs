/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ncraft.grs.wcprocessor.application;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ncraft.grs.common.config.AppProcessorProperties;
import org.ncraft.grs.common.event.DataSubmittedEvent;
import org.ncraft.grs.common.exception.EventPublishingException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class BatchProcessingCoordinator {

    private final AtomicInteger evaluationCounter = new AtomicInteger(0);
    private final ProcessingService processingService;
    private final AppProcessorProperties appProcessorProperties;

    public BatchProcessingCoordinator(ProcessingService processingService,
                                      AppProcessorProperties appProcessorProperties) {
        this.processingService = processingService;
        this.appProcessorProperties = appProcessorProperties;
    }

    @PostConstruct
    public void logConfiguration() {
        try {
            Duration duration = appProcessorProperties.sweepDelay();
            log.info("Batch Processing Coordinator Initialized. Failsafe sweep every: {} minutes, {} seconds",
                    duration.toMinutes(), duration.toSecondsPart());
        } catch (Exception e) {
            log.warn("Using default sweep delay.");
        }
    }

    /**
     * Processes bursts of traffic in near real-time.
     */
    @Async("eventTaskExecutor")
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDataSubmitted(DataSubmittedEvent event) {
        try {
            if (evaluationCounter.incrementAndGet() >= appProcessorProperties.intakeThreshold()) {
                log.debug("Intake threshold reached. Triggering processing.");
                triggerProcessing();
            }
        } catch (Exception e) {
            throw new EventPublishingException("Listener failed to process webhook event", e);
        }
    }

    /**
     * Executes the failsafe sweep to recover and retry failed events.
     * <p>
     * The execution interval is configurable via the {@code app.processor.sweep-delay} property.
     * If not specified, it defaults to 60 minutes.
     * <p>
     * Note: The configuration value must be provided as a standard ISO-8601 duration string
     * (e.g., 'PT60M' for 60 minutes, 'PT2H' for 2 hours).
     */
    @Scheduled(fixedDelayString = "${app.processor.sweep-delay:PT60M}")
    public void unconditionalFailsafeSweep() {
        log.debug("Running unconditional safety sweep...");
        triggerProcessing();
    }

    /**
     * Both the Event Listener and the Timer call this method.
     * `synchronized` ensures that if the Timer and the Threshold trigger
     * at the exact same time, they take turns, preventing database collision.
     */
    private synchronized void triggerProcessing() {
        evaluationCounter.set(0);
        processingService.executeChunkedProcessing();
    }
}