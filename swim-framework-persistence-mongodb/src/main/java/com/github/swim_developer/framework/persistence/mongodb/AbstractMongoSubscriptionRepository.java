package com.github.swim_developer.framework.persistence.mongodb;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.swim_developer.framework.application.port.out.SwimConsumerSubscriptionRepository;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SubscriptionType;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractMongoSubscriptionRepository<D extends MongoSubscriptionDocumentPort>
        implements PanacheMongoRepository<D>, SwimConsumerSubscriptionRepository<D> {

    private final Cache<String, D> cache = Caffeine.newBuilder().build();
    private final ConcurrentHashMap<String, String> queueIndex = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    protected String getSubscriptionIdFrom(D doc) {
        return doc.getSubscriptionId();
    }

    protected String getQueueNameFrom(D doc) {
        return doc.getQueueName();
    }

    protected String getSubscriptionStatusFrom(D doc) {
        return doc.getSubscriptionStatus();
    }

    protected void setSubscriptionStatusOn(D doc, String status) {
        doc.setSubscriptionStatus(status);
    }

    protected String getTypeFrom(D doc) {
        return doc.getType();
    }

    private void ensureLoaded() {
        if (!cacheLoaded) {
            synchronized (this) {
                if (!cacheLoaded) {
                    listAll().forEach(this::addToCache);
                    cacheLoaded = true;
                    log.info("Subscription cache loaded: {} entries", cache.estimatedSize());
                }
            }
        }
    }

    private void addToCache(D doc) {
        cache.put(getSubscriptionIdFrom(doc), doc);
        String qn = getQueueNameFrom(doc);
        if (qn != null) {
            queueIndex.put(qn, getSubscriptionIdFrom(doc));
        }
    }

    private void removeFromCache(String subscriptionId) {
        D removed = cache.getIfPresent(subscriptionId);
        if (removed != null) {
            String qn = getQueueNameFrom(removed);
            if (qn != null) {
                queueIndex.remove(qn);
            }
        }
        cache.invalidate(subscriptionId);
    }

    @Override
    public List<D> findAllSubscriptions() {
        ensureLoaded();
        return List.copyOf(cache.asMap().values());
    }

    @Override
    public Optional<D> findBySubscriptionId(String subscriptionId) {
        ensureLoaded();
        return Optional.ofNullable(cache.getIfPresent(subscriptionId));
    }

    @Override
    public List<D> findActiveSubscriptions() {
        ensureLoaded();
        return cache.asMap().values().stream()
                .filter(doc -> SubscriptionStatus.ACTIVE.name().equals(getSubscriptionStatusFrom(doc)))
                .toList();
    }

    @Override
    public List<D> findDeclaredSubscriptions() {
        ensureLoaded();
        return cache.asMap().values().stream()
                .filter(doc -> SubscriptionType.DECLARED.name().equals(getTypeFrom(doc)))
                .toList();
    }

    @Override
    public Optional<D> findByQueueName(String queueName) {
        ensureLoaded();
        String id = queueIndex.get(queueName);
        return id != null ? Optional.ofNullable(cache.getIfPresent(id)) : Optional.empty();
    }

    @Override
    public Optional<D> findByConfigHashAndType(String configHash, String type) {
        return find("configHash = ?1 and type = ?2", configHash, type).firstResultOptional();
    }

    @Override
    public Optional<D> findByConfigHash(String configHash) {
        return find("configHash", configHash).firstResultOptional();
    }

    @Override
    public List<D> findBySubscriptionEndBefore(Instant threshold) {
        return find("subscriptionEnd < ?1", threshold).list();
    }

    @Override
    public void persistSubscription(D entity) {
        persist(entity);
        addToCache(entity);
    }

    @Override
    public void updateSubscription(D entity) {
        update(entity);
        addToCache(entity);
    }

    @Override
    public boolean deleteBySubscriptionId(String subscriptionId) {
        boolean deleted = delete("subscriptionId", subscriptionId) > 0;
        if (deleted) {
            removeFromCache(subscriptionId);
        }
        return deleted;
    }

    @Override
    public void updateStatus(String subscriptionId, String newStatus) {
        find("subscriptionId", subscriptionId).firstResultOptional().ifPresent(doc -> {
            setSubscriptionStatusOn(doc, newStatus);
            update(doc);
            addToCache(doc);
        });
    }

    @Override
    public long countSubscriptions() {
        ensureLoaded();
        return cache.estimatedSize();
    }

    @Override
    public void deleteAllSubscriptions() {
        deleteAll();
        clearCache();
    }

    @Override
    public void clearCache() {
        cache.invalidateAll();
        queueIndex.clear();
        cacheLoaded = false;
    }
}
