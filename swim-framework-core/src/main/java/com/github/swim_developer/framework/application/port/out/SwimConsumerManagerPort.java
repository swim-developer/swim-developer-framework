package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;

public interface SwimConsumerManagerPort {

    void registerConsumer(String subscriptionId, String queueName, ProviderConfiguration provider);

    void pauseConsumer(String subscriptionId);

    void unregisterConsumer(String subscriptionId);

    boolean isConnected();

    int getActiveConsumerCount();

    String getConnectedProviders();

    boolean hasZombieConsumers();

    void resetClient();
}
