package com.github.swim_developer.framework.consumer.application.subscription.service;

public interface SwimSubscriptionLifecyclePort {

    void resetAllSubscriptions(boolean deleteAndRecreate);

    void registerAllActiveConsumers();

    void populateFilterCache();
}
