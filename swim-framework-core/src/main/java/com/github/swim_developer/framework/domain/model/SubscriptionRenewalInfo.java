package com.github.swim_developer.framework.domain.model;


import java.time.Instant;

public record SubscriptionRenewalInfo(
        String subscriptionId,
        Instant subscriptionEnd
) {
}
