package com.github.swim_developer.framework.application.port.out;

public interface SwimSubscriptionQueuePort {

    void provision(String queueName, String userId);

    void deprovision(String queueName);
}
