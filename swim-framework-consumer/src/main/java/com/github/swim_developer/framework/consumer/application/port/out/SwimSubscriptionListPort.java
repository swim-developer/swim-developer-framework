package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;

import java.util.List;
import java.util.Optional;

public interface SwimSubscriptionListPort {

    List<? extends SwimConsumerSubscription> findActiveSubscriptions();

    Optional<? extends SwimConsumerSubscription> findBySubscriptionId(String subscriptionId);
}
