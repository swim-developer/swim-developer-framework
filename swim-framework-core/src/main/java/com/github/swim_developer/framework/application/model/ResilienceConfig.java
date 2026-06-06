package com.github.swim_developer.framework.application.model;

import lombok.Builder;

@Builder
public record ResilienceConfig(
    int connectTimeoutMs,
    int readTimeoutMs,
    int retryMaxAttempts,
    long retryDelayMs
) {
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    public static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_DELAY_MS = 1000;

    public int effectiveConnectTimeoutMs() {
        return connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    public int effectiveReadTimeoutMs() {
        return readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    }

    public int effectiveRetryMaxAttempts() {
        return retryMaxAttempts > 0 ? retryMaxAttempts : DEFAULT_RETRY_MAX_ATTEMPTS;
    }

    public long effectiveRetryDelayMs() {
        return retryDelayMs > 0 ? retryDelayMs : DEFAULT_RETRY_DELAY_MS;
    }
}
