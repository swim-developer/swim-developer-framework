package com.github.swim_developer.framework.application.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubscriptionStatusUpdate(
        @JsonProperty("subscription_status")
        String subscriptionStatus,
        String comment
) {
    public SubscriptionStatusUpdate(String subscriptionStatus) {
        this(subscriptionStatus, null);
    }
}
