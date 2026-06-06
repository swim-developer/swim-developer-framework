package com.github.swim_developer.framework.domain.model;

import java.util.UUID;
import java.util.function.Predicate;

public interface SwimSubscription<E> {

    UUID getSubscriptionId();

    String getQueue();

    QualityOfService getQos();

    SubscriptionStatus getStatus();

    default String getProviderName() {
        return null;
    }

    default String getHeartbeatTopic() {
        return null;
    }

    Predicate<E> toFilter();

    default Boolean isActive(){
        return SubscriptionStatus.ACTIVE == getStatus();
    }
}
