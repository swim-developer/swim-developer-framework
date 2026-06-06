package com.github.swim_developer.framework.domain.model;


import java.time.Instant;

public record SubscriptionExpiry(
        String subscriptionId,
        Instant subscriptionEnd,
        String currentStatus
) {
}
