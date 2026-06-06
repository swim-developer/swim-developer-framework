package com.github.swim_developer.framework.domain.model;

import java.util.Map;
import java.util.Set;

public interface SwimConsumerSubscription {

    String getSubscriptionId();

    String getQueueName();

    String getProviderId();

    default Map<String, Set<String>> projectFilterDimensions() {
        return Map.of();
    }
}
