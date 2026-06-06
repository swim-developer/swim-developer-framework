package com.github.swim_developer.framework.consumer.infrastructure.out.filter;

import com.github.swim_developer.framework.application.port.out.SubscriptionFilterProvider;
import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionListPort;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@DefaultBean
@ApplicationScoped
public class DefaultSubscriptionFilterProvider implements SubscriptionFilterProvider {

    private final Instance<SwimSubscriptionListPort> storeInstance;

    @Inject
    public DefaultSubscriptionFilterProvider(Instance<SwimSubscriptionListPort> storeInstance) {
        this.storeInstance = storeInstance;
    }

    @Override
    public Map<String, Map<String, Set<String>>> loadAllFilters() {
        if (!storeInstance.isResolvable()) {
            log.warn("No SwimSubscriptionListPort available, returning empty filters");
            return Map.of();
        }
        Map<String, Map<String, Set<String>>> result = new HashMap<>();
        storeInstance.get().findActiveSubscriptions()
                .forEach(sub -> result.put(sub.getSubscriptionId(), sub.projectFilterDimensions()));
        return result;
    }

    @Override
    public Optional<Map<String, Set<String>>> loadFiltersForSubscription(String subscriptionId) {
        if (!storeInstance.isResolvable()) {
            return Optional.empty();
        }
        return storeInstance.get().findBySubscriptionId(subscriptionId)
                .map(SwimConsumerSubscription::projectFilterDimensions);
    }
}
