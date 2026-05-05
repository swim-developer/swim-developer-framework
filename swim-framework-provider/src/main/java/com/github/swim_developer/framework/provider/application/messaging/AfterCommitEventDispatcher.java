package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import io.vertx.core.Vertx;
import jakarta.transaction.Synchronization;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AfterCommitEventDispatcher implements Synchronization {

    private final String eventId;
    private final Object entity;
    private final HandoffCache handoffCache;
    private final Vertx vertx;
    private final String outboxAddress;

    public AfterCommitEventDispatcher(String eventId, Object entity,
                                          HandoffCache handoffCache, Vertx vertx,
                                          String outboxAddress) {
        this.eventId = eventId;
        this.entity = entity;
        this.handoffCache = handoffCache;
        this.vertx = vertx;
        this.outboxAddress = outboxAddress;
    }

    @Override
    public void beforeCompletion() {
        // Intentionally empty - no action needed before transaction completion
    }

    @Override
    public void afterCompletion(int status) {
        if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
            handoffCache.put(eventId, entity);
            vertx.eventBus().publish(outboxAddress, eventId);
            log.debug("Event dispatched after commit - EventId: {}, Address: {}", eventId, outboxAddress);
        } else {
            log.warn("Transaction rolled back, event not dispatched - EventId: {}", eventId);
        }
    }
}
