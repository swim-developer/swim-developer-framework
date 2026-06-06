package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public abstract class AbstractOutboxEventProcessor<E extends SwimProviderEvent> {

    private final HandoffCache handoffCache;
    private final MeterRegistry registry;

    @Inject
    protected AbstractOutboxEventProcessor(HandoffCache handoffCache, MeterRegistry registry) {
        this.handoffCache = handoffCache;
        this.registry = registry;
    }

    protected void processWithMetrics(String eventId) {
        Timer.Sample timerSample = Timer.start(registry);
        String finalStatus = EventStatus.RECEIVED.name();
        Span.current().setAttribute(getMetricPrefix() + ".eventId", eventId);

        try {
            processOutboxEvent(eventId);
            E entity = findEntityById(eventId);
            if (entity != null) {
                finalStatus = entity.getStatus().name();
            }
            Span.current().setAttribute(getMetricPrefix() + ".delivery.status", finalStatus);
        } catch (Exception e) {
            finalStatus = EventStatus.RECEIVED.name();
            Span.current().setAttribute(getMetricPrefix() + ".delivery.status", "DELIVERY_FAILED");
            Span.current().recordException(e);
            log.error("Outbox processing failed - EventId: {}", eventId, e);
            incrementFailedCounter();
            handleFailure(eventId);
        } finally {
            timerSample.stop(Timer.builder(getMetricPrefix() + "_outbox_processing_duration")
                    .description("Total time to process outbox event")
                    .tag("status", finalStatus)
                    .register(registry));
        }
    }

    public void processOutboxEvent(String eventId) {
        try {
            E entity = loadEntity(eventId);
            if (entity == null) {
                log.error("Event not found: {}", eventId);
                return;
            }

            DeliveryResult result = deliver(entity);
            updateEntityState(entity, result);

            log.info("Outbox processed - EventId: {}, Delivered: {}, Failed: {}",
                    eventId, result.delivered(), result.failed());
        } catch (Exception e) {
            log.error("Outbox processing failed - EventId: {}", eventId, e);
            handleFailure(eventId);
        }
    }

    @Transactional
    protected E loadEntity(String eventId) {
        Optional<E> cached = handoffCache.getAndRemove(eventId, getEntityClass());

        if (cached.isPresent()) {
            log.debug("Event retrieved from handoff cache - EventId: {}", eventId);
            return mergeEntity(cached.get());
        }

        log.debug("Event retrieved from database (cache miss) - EventId: {}", eventId);
        return findEntityById(eventId);
    }

    @Transactional
    protected void updateEntityState(E entity, DeliveryResult result) {
        E managed = findEntityById(entity.getEventId());
        if (managed == null) {
            log.warn("Cannot update event state - entity not found: {}", entity.getEventId());
            return;
        }

        managed.setDeliveredCount(result.delivered());
        managed.setProcessedAt(Instant.now());

        if (result.delivered() == 0 && result.failed() > 0) {
            managed.setRetryCount(managed.getRetryCount() + 1);
        } else if (result.delivered() > 0 && result.failed() > 0) {
            managed.setStatus(EventStatus.PARTIALLY_DELIVERED);
        } else {
            managed.setStatus(EventStatus.DELIVERED);
        }

        if (result.delivered() == 0 && result.failed() > 0) {
            incrementFailedCounter();
        }

        log.info("Event state updated - EventId: {}, Status: {}, Delivered: {}, Failed: {}",
                managed.getEventId(), managed.getStatus(), result.delivered(), result.failed());
    }

    @Transactional
    protected void handleFailure(String eventId) {
        try {
            E entity = findEntityById(eventId);
            if (entity != null) {
                entity.setRetryCount(entity.getRetryCount() + 1);
                log.warn("Incremented retry count for event: {} (retries: {})",
                        eventId, entity.getRetryCount());
            }
        } catch (Exception e) {
            log.error("Failed to update retry count for event: {}", eventId, e);
        }
    }

    protected void incrementFailedCounter() {
        Counter.builder(getMetricPrefix() + "_events_delivery_failed_total")
                .description("Total events that failed delivery")
                .register(registry)
                .increment();
    }

    protected abstract E findEntityById(String eventId);

    protected abstract E mergeEntity(E detachedEntity);

    protected abstract Class<E> getEntityClass();

    protected abstract String getMetricPrefix();

    protected abstract DeliveryResult deliver(E entity);
}
