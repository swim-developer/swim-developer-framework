package com.github.swim_developer.framework.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for SWIM subscription domain entities managed by
 * {@code AbstractProviderSubscriptionService}.
 *
 * <p>Implementing this interface allows the framework to access and mutate
 * the standard subscription lifecycle fields without requiring subclasses
 * to repeat the same abstract accessor/mutator boilerplate.</p>
 */
public interface SwimSubscriptionEntity {

    UUID getSubscriptionId();

    String getUserId();

    String getQueue();

    SubscriptionStatus getStatus();

    void setStatus(SubscriptionStatus status);

    void setUpdatedAt(Instant updatedAt);

    void setSubscriptionEnd(Instant subscriptionEnd);

    Instant getSubscriptionEnd();
}
