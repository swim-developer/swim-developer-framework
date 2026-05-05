package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
class QueueCircuitState {
    private int consecutiveFailures;
    private int halfOpenSuccesses;
    private boolean open;
    private Instant openedAt;

    synchronized boolean isOpen(long openDurationMillis) {
        return isOpen(openDurationMillis, Instant.now());
    }

    synchronized boolean isOpen(long openDurationMillis, Instant now) {
        if (!open) {
            return false;
        }
        if (now.isAfter(openedAt.plusMillis(openDurationMillis))) {
            open = false;
            halfOpenSuccesses = 0;
            return false;
        }
        return true;
    }

    synchronized void recordSuccess(int successThreshold) {
        consecutiveFailures = 0;
        halfOpenSuccesses++;
        if (halfOpenSuccesses >= successThreshold) {
            open = false;
            halfOpenSuccesses = 0;
        }
    }

    synchronized void recordFailure(int failureThreshold) {
        consecutiveFailures++;
        halfOpenSuccesses = 0;
        if (consecutiveFailures >= failureThreshold) {
            open = true;
            openedAt = Instant.now();
            log.warn("Circuit opened for queue after {} consecutive failures", consecutiveFailures);
        }
    }
}
