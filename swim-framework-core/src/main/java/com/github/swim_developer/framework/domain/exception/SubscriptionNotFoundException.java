package com.github.swim_developer.framework.domain.exception;

public class SubscriptionNotFoundException extends RuntimeException {

    private final String subscriptionId;

    public SubscriptionNotFoundException(String subscriptionId) {
        super("Subscription not found on provider: " + subscriptionId);
        this.subscriptionId = subscriptionId;
    }

    public SubscriptionNotFoundException(String subscriptionId, Throwable cause) {
        super("Subscription not found on provider: " + subscriptionId, cause);
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
