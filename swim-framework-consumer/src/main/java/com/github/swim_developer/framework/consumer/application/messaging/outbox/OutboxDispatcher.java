package com.github.swim_developer.framework.consumer.application.messaging.outbox;

import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

@Slf4j
@ApplicationScoped
public class OutboxDispatcher {

    private final HandoffCache handoffCache;
    private final Vertx vertx;

    @Inject
    public OutboxDispatcher(HandoffCache handoffCache, Vertx vertx) {
        this.handoffCache = handoffCache;
        this.vertx = vertx;
    }

    public void dispatch(ObjectId eventId, Object event, String address) {
        String eventIdStr = eventId.toHexString();
        handoffCache.put(eventIdStr, event);
        vertx.eventBus().publish(address, eventIdStr);
        log.debug("Event dispatched to outbox - EventId: {}, Address: {}", eventIdStr, address);
    }
}
