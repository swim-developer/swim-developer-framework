package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.application.service.AbstractOutboxScheduler;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Slf4j
public abstract class AbstractProviderOutboxScheduler<E extends SwimProviderEvent>
        extends AbstractOutboxScheduler<E> {

    private final HandoffCache handoffCache;
    private final Vertx vertx;
    private final MeterRegistry registry;

    protected AbstractProviderOutboxScheduler() {
        this.handoffCache = null;
        this.vertx = null;
        this.registry = null;
    }

    @Inject
    protected AbstractProviderOutboxScheduler(
            HandoffCache handoffCache,
            Vertx vertx,
            MeterRegistry registry,
            LeaderElection leaderElection,
            @ConfigProperty(name = "swim.outbox.recovery.batch-size", defaultValue = "100") int recoveryBatchSize,
            @ConfigProperty(name = "swim.outbox.recovery.max-retries", defaultValue = "5") int maxRetries) {
        super(leaderElection, maxRetries, recoveryBatchSize);
        this.handoffCache = handoffCache;
        this.vertx = vertx;
        this.registry = registry;
    }

    @Override
    protected boolean exceedsMaxRetries(E event) {
        return event.getRetryCount() >= getMaxRetries();
    }

    @Override
    protected void handleMaxRetriesExceeded(E event) {
        event.setStatus(EventStatus.DEAD_LETTER);
        log.error("Event {} exceeded max retries ({}), marked as DEAD_LETTER",
                event.getEventId(), getMaxRetries());
        Counter.builder(getMetricPrefix() + "_outbox_deadletter_total")
                .description("Total events that exceeded max retries")
                .register(registry)
                .increment();
    }

    @Override
    protected void redispatch(E event) {
        try {
            handoffCache.put(event.getEventId(), event);
            vertx.eventBus().publish(getOutboxEventAddress(), event.getEventId());
            Counter.builder(getMetricPrefix() + "_outbox_recovery_total")
                    .description("Total orphaned events recovered by scheduler")
                    .register(registry)
                    .increment();
            log.info("Orphaned event redispatched - EventId: {}, RetryCount: {}",
                    event.getEventId(), event.getRetryCount());
        } catch (Exception e) {
            log.error("Failed to redispatch orphaned event - EventId: {}", event.getEventId(), e);
        }
    }

    protected abstract String getOutboxEventAddress();

    protected abstract String getMetricPrefix();

    /**
     * Hook for services that need a different batch size during periodic cleanup
     * (e.g., purging dead-letter events) versus recovery redispatch.
     *
     * <p>Defaults to {@link #getBatchSize()}. Override when cleanup throughput
     * should differ from the recovery throughput.</p>
     */
    protected int getCleanupBatchSize() {
        return getBatchSize();
    }

    /**
     * Hook for services that compress event payloads before storing them in the
     * outbox (e.g., large AIXM messages stored with GZIP).
     *
     * <p>Returns {@code false} by default (no compression). Services that
     * enable compression must also override payload decompression logic in their
     * outbox event processor.</p>
     */
    protected boolean isPayloadCompressionEnabled() {
        return false;
    }
}
