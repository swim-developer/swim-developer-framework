package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class PerQueueCircuitBreaker {

    private final int failureThreshold;
    private final long openDurationMillis;
    private final int successThreshold;
    private final ConcurrentHashMap<String, QueueCircuitState> states = new ConcurrentHashMap<>();

    public PerQueueCircuitBreaker(int failureThreshold, long openDurationMillis, int successThreshold) {
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
        this.successThreshold = successThreshold;
    }

    public boolean isOpen(String queue) {
        return isOpen(queue, Instant.now());
    }

    public boolean isOpen(String queue, Instant now) {
        QueueCircuitState state = states.get(queue);
        if (state == null) {
            return false;
        }
        return state.isOpen(openDurationMillis, now);
    }

    public void recordSuccess(String queue) {
        QueueCircuitState state = states.get(queue);
        if (state != null) {
            state.recordSuccess(successThreshold);
        }
    }

    public void recordFailure(String queue) {
        QueueCircuitState state = states.computeIfAbsent(queue, k -> new QueueCircuitState());
        state.recordFailure(failureThreshold);
    }

}
