package com.github.swim_developer.framework.consumer.infrastructure.out.filter;

import com.github.swim_developer.framework.application.port.out.SubscriptionFilterProvider;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class SubscriptionFilterCache implements com.github.swim_developer.framework.application.port.out.SwimSubscriptionFilterPort {

    private final ConcurrentHashMap<String, Map<String, Set<String>>> filters = new ConcurrentHashMap<>();
    private final Instance<SubscriptionFilterProvider> providerInstance;
    private final MeterRegistry meterRegistry;

    @Inject
    public SubscriptionFilterCache(
            Instance<SubscriptionFilterProvider> providerInstance,
            MeterRegistry meterRegistry) {
        this.providerInstance = providerInstance;
        this.meterRegistry = meterRegistry;
    }

    public SubscriptionFilterCache() {
        this.providerInstance = null;
        this.meterRegistry = null;
    }

    @PostConstruct
    void registerMetrics() {
        if (meterRegistry != null) {
            Gauge.builder("swim_filter_cache_size", this, SubscriptionFilterCache::size)
                    .description("Number of subscriptions in filter cache")
                    .register(meterRegistry);
        }
    }

    public void updateFilters(String subscriptionId, String dimension, Collection<String> allowedValues) {
        Set<String> values = (allowedValues != null && !allowedValues.isEmpty())
                ? Set.copyOf(allowedValues)
                : Set.of();
        filters.computeIfAbsent(subscriptionId, k -> new ConcurrentHashMap<>()).put(dimension, values);
        log.debug("Filter updated - SubscriptionId: {}, Dimension: {}, AllowedValues: {}", subscriptionId, dimension, values);
    }

    public void removeSubscription(String subscriptionId) {
        filters.remove(subscriptionId);
    }

    public boolean isAllowed(String subscriptionId, String dimension, String value) {
        Map<String, Set<String>> dimensions = resolveDimensions(subscriptionId);
        if (dimensions == null) {
            return true;
        }
        Set<String> allowed = dimensions.get(dimension);
        return allowed == null || allowed.isEmpty() || allowed.contains(value);
    }

    private Map<String, Set<String>> resolveDimensions(String subscriptionId) {
        Map<String, Set<String>> dimensions = filters.get(subscriptionId);
        return dimensions != null ? dimensions : loadFromProvider(subscriptionId);
    }

    public void refreshAll() {
        if (!isProviderAvailable()) {
            log.warn("No SubscriptionFilterProvider available - cannot refresh cache");
            return;
        }
        Map<String, Map<String, Set<String>>> loaded = providerInstance.get().loadAllFilters();
        Map<String, Map<String, Set<String>>> replacement = new ConcurrentHashMap<>();
        loaded.forEach((subscriptionId, dimensions) ->
                replacement.put(subscriptionId, new ConcurrentHashMap<>(dimensions)));
        filters.clear();
        filters.putAll(replacement);
        log.info("Filter cache refreshed from database - {} subscriptions loaded", filters.size());
    }

    public void clear() {
        filters.clear();
    }

    public int size() {
        return filters.size();
    }

    private Map<String, Set<String>> loadFromProvider(String subscriptionId) {
        if (!isProviderAvailable()) {
            return Map.of();
        }
        return providerInstance.get().loadFiltersForSubscription(subscriptionId)
                .map(dimensions -> {
                    var cached = new ConcurrentHashMap<>(dimensions);
                    filters.put(subscriptionId, cached);
                    log.debug("Filter cache miss resolved from database - SubscriptionId: {}", subscriptionId);
                    return (Map<String, Set<String>>) cached;
                })
                .orElse(Map.of());
    }

    private boolean isProviderAvailable() {
        return providerInstance != null && providerInstance.isResolvable();
    }
}
