package com.github.swim_developer.framework.domain.exception;

public class SubscriptionConflictException extends RuntimeException {

    private final String subscriptionId;

    public SubscriptionConflictException(String subscriptionId) {
        super("Subscription already exists on provider: " + subscriptionId);
        this.subscriptionId = subscriptionId;
    }

    public SubscriptionConflictException(String subscriptionId, Throwable cause) {
        super("Subscription already exists on provider: " + subscriptionId, cause);
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
