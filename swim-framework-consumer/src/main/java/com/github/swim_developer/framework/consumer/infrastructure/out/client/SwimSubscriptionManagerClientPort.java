package com.github.swim_developer.framework.consumer.infrastructure.out.client;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;

public interface SwimSubscriptionManagerClientPort {

    String querySubscriptionStatus(String subscriptionId, ProviderConfiguration provider);
}
