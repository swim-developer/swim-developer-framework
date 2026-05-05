package com.github.swim_developer.framework.consumer.application.port.out;

public interface SwimSubscriptionCountPort {

    long countActiveSubscriptions();

    long countTotalSubscriptions();
}
