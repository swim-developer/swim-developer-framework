package com.github.swim_developer.framework.application.port.out;

public interface SwimIdempotencyPort {

    boolean isAlreadyProcessed(String subscriptionId, String contentHash);

    void markAsProcessed(String subscriptionId, String contentHash);

    void warmup();
}
