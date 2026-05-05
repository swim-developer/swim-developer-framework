package com.github.swim_developer.framework.infrastructure.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class HandoffCache {

    private static final long DEFAULT_TTL_SECONDS = 60;

    private static final long DEFAULT_MAX_SIZE = 50_000;

    private final Cache<String, Object> cache;
    private final MeterRegistry registry;

    @Inject
    public HandoffCache(MeterRegistry registry) {
        this.registry = registry;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(DEFAULT_TTL_SECONDS))
                .maximumSize(DEFAULT_MAX_SIZE)
                .recordStats()
                .build();
    }

    @PostConstruct
    void registerMetrics() {
        CaffeineCacheMetrics.monitor(registry, cache, "handoff_cache");
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAndRemove(String key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value != null && type.isInstance(value)) {
            cache.invalidate(key);
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    public void remove(String key) {
        cache.invalidate(key);
    }
}
