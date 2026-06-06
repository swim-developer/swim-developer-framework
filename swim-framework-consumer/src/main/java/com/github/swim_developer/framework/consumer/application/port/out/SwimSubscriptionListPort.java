package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;

import java.util.List;
import java.util.Optional;

public interface SwimSubscriptionListPort {

    @SuppressWarnings("java:S1452")
    List<? extends SwimConsumerSubscription> findActiveSubscriptions();

    @SuppressWarnings("java:S1452")
    Optional<? extends SwimConsumerSubscription> findBySubscriptionId(String subscriptionId);
}
