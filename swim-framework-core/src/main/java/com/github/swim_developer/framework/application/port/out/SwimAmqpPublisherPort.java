package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.QualityOfService;

import java.util.UUID;

public interface SwimAmqpPublisherPort {

    void publish(String address, String payload, QualityOfService qos);

    void publishToQueue(String queue, String payload, QualityOfService qos, UUID subscriptionId);

    boolean isHealthy();
}
