package com.github.swim_developer.framework.application.port.out;

import java.util.Collection;

public interface SwimSubscriptionFilterPort {

    void updateFilters(String subscriptionId, String dimension, Collection<String> allowedValues);

    boolean isAllowed(String subscriptionId, String dimension, String value);

    void removeSubscription(String subscriptionId);

    void clear();

    int size();
}
