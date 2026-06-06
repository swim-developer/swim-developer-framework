package com.github.swim_developer.framework.consumer.infrastructure.out.idempotency;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@ApplicationScoped
public class AbstractIdempotencyCache implements com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort {

    private static final int WARMUP_HOURS = 24;
    private static final int WARMUP_LIMIT = 50_000;
    private final AtomicBoolean warmedUp = new AtomicBoolean(false);

    private final CacheManager cacheManager;
    private final SwimIdempotencyEventPort eventPort;
    private final String cacheName;

    @Inject
    public AbstractIdempotencyCache(
            CacheManager cacheManager,
            SwimIdempotencyEventPort eventPort,
            @ConfigProperty(name = "swim.idempotency.cache-name") String cacheName) {
        this.cacheManager = cacheManager;
        this.eventPort = eventPort;
        this.cacheName = cacheName;
    }

    private Cache getCache() {
        return cacheManager.getCache(cacheName)
                .orElseThrow(() -> new IllegalStateException("Cache not found: " + cacheName));
    }

    private String compositeKey(String subscriptionId, String contentHash) {
        return subscriptionId + ":" + contentHash;
    }

    @Override
    public boolean isAlreadyProcessed(String subscriptionId, String contentHash) {
        String cacheKey = compositeKey(subscriptionId, contentHash);
        CaffeineCache caffeineCache = getCache().as(CaffeineCache.class);
        CompletableFuture<Object> cachedFuture = caffeineCache.getIfPresent(cacheKey);

        if (cachedFuture != null) {
            Object cached = cachedFuture.join();
            if (Boolean.TRUE.equals(cached)) {
                return true;
            }
        }

        boolean existsInDb = eventPort.existsBySubscriptionAndContentHash(subscriptionId, contentHash);
        if (existsInDb) {
            caffeineCache.put(cacheKey, CompletableFuture.completedFuture(Boolean.TRUE));
        }
        return existsInDb;
    }

    @Override
    public void markAsProcessed(String subscriptionId, String contentHash) {
        String cacheKey = compositeKey(subscriptionId, contentHash);
        CaffeineCache caffeineCache = getCache().as(CaffeineCache.class);
        caffeineCache.put(cacheKey, CompletableFuture.completedFuture(Boolean.TRUE));
    }

    public void warmup() {
        if (!warmedUp.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant since = Instant.now().minus(Duration.ofHours(WARMUP_HOURS));
            List<String> recentKeys = eventPort.findRecentCacheKeys(since, WARMUP_LIMIT);
            if (recentKeys.isEmpty()) {
                log.info("Idempotency cache warmup: no recent entries found");
                return;
            }
            CaffeineCache caffeineCache = getCache().as(CaffeineCache.class);
            recentKeys.forEach(key ->
                    caffeineCache.put(key, CompletableFuture.completedFuture(Boolean.TRUE)));
            log.info("Idempotency cache warmed with {} recent entries", recentKeys.size());
        } catch (Exception e) {
            warmedUp.set(false);
            log.warn("Idempotency cache warmup failed (will retry): {}", e.getMessage());
        }
    }
}
