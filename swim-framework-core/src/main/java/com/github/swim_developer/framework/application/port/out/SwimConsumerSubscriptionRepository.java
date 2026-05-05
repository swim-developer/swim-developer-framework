package com.github.swim_developer.framework.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SwimConsumerSubscriptionRepository<D> {

    List<D> findAllSubscriptions();

    Optional<D> findBySubscriptionId(String subscriptionId);

    List<D> findActiveSubscriptions();

    List<D> findDeclaredSubscriptions();

    Optional<D> findByQueueName(String queueName);

    Optional<D> findByConfigHashAndType(String configHash, String type);

    Optional<D> findByConfigHash(String configHash);

    List<D> findBySubscriptionEndBefore(Instant threshold);

    void persistSubscription(D entity);

    void updateSubscription(D entity);

    boolean deleteBySubscriptionId(String subscriptionId);

    void updateStatus(String subscriptionId, String newStatus);

    long countSubscriptions();

    void deleteAllSubscriptions();

    void clearCache();
}
