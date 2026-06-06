package com.github.swim_developer.framework.infrastructure.out.resilience;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ProviderCircuitBreaker {

    static final int DEFAULT_FAILURE_THRESHOLD = 5;
    static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(30);

    private final int failureThreshold;
    private final Duration cooldown;
    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> circuitOpenUntil = new ConcurrentHashMap<>();

    public ProviderCircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN);
    }

    public ProviderCircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
    }

    public boolean isOpen(String providerId) {
        return isOpen(providerId, Instant.now());
    }

    public boolean isOpen(String providerId, Instant now) {
        Instant openUntil = circuitOpenUntil.get(providerId);
        if (openUntil == null) {
            return false;
        }
        if (now.isAfter(openUntil)) {
            log.info("Circuit breaker HALF-OPEN for provider '{}' — allowing probe request", providerId);
            circuitOpenUntil.remove(providerId);
            return false;
        }
        return true;
    }

    public void recordSuccess(String providerId) {
        AtomicInteger counter = failureCounts.get(providerId);
        if (counter != null && counter.get() > 0) {
            counter.set(0);
            circuitOpenUntil.remove(providerId);
            log.info("Circuit breaker CLOSED for provider '{}'", providerId);
        }
    }

    public void recordFailure(String providerId) {
        int failures = failureCounts
                .computeIfAbsent(providerId, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (failures >= failureThreshold && !circuitOpenUntil.containsKey(providerId)) {
            Instant until = Instant.now().plus(cooldown);
            circuitOpenUntil.put(providerId, until);
            log.warn("Circuit breaker OPEN for provider '{}' — {} consecutive failures, cooldown {}s",
                    providerId, failures, cooldown.toSeconds());
        }
    }

    public int getFailureCount(String providerId) {
        AtomicInteger counter = failureCounts.get(providerId);
        return counter != null ? counter.get() : 0;
    }

    public void reset(String providerId) {
        failureCounts.remove(providerId);
        circuitOpenUntil.remove(providerId);
    }
}
