package com.github.swim_developer.framework.consumer.infrastructure.out.filter;

import com.github.swim_developer.framework.application.port.out.SubscriptionFilterProvider;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractSubscriptionFilterProvider<S extends SwimConsumerSubscription>
        implements SubscriptionFilterProvider {

    protected abstract List<S> findActiveSubscriptions();

    protected abstract Optional<S> findBySubscriptionId(String subscriptionId);

    protected abstract Map<String, Set<String>> projectFilters(S subscription);

    @Override
    public Map<String, Map<String, Set<String>>> loadAllFilters() {
        Map<String, Map<String, Set<String>>> result = new HashMap<>();
        findActiveSubscriptions()
                .forEach(sub -> result.put(sub.getSubscriptionId(), projectFilters(sub)));
        return result;
    }

    @Override
    public Optional<Map<String, Set<String>>> loadFiltersForSubscription(String subscriptionId) {
        return findBySubscriptionId(subscriptionId)
                .map(this::projectFilters);
    }
}
