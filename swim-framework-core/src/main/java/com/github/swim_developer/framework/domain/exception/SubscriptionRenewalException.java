package com.github.swim_developer.framework.domain.exception;

public class SubscriptionRenewalException extends RuntimeException {

    private final String subscriptionId;

    public SubscriptionRenewalException(String subscriptionId, String message) {
        super("Failed to renew subscription " + subscriptionId + ": " + message);
        this.subscriptionId = subscriptionId;
    }

    public SubscriptionRenewalException(String subscriptionId, Throwable cause) {
        super("Failed to renew subscription " + subscriptionId, cause);
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
