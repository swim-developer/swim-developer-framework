package com.github.swim_developer.framework.application.model;

import lombok.Builder;

@Builder
public record ProviderConfiguration(
    String providerId,
    SubscriptionManagerConfig subscriptionManager,
    AmqpBrokerConfig amqpBroker
) {
}
