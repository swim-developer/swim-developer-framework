package com.github.swim_developer.framework.application.port.out;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SubscriptionFilterProvider {

    Map<String, Map<String, Set<String>>> loadAllFilters();

    Optional<Map<String, Set<String>>> loadFiltersForSubscription(String subscriptionId);
}
