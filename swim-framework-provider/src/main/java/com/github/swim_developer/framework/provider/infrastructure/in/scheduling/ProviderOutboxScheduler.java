package com.github.swim_developer.framework.provider.infrastructure.in.scheduling;

import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.provider.application.messaging.AbstractProviderOutboxScheduler;
import com.github.swim_developer.framework.provider.application.port.out.SwimProviderEventStorePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Slf4j
@ApplicationScoped
public class ProviderOutboxScheduler extends AbstractProviderOutboxScheduler<SwimProviderEvent> {

    private final Instance<SwimProviderEventStorePort> eventStores;
    private final String outboxEventAddress;
    private final String metricPrefix;

    protected ProviderOutboxScheduler() {
        this.eventStores = null;
        this.outboxEventAddress = null;
        this.metricPrefix = null;
    }

    @Inject
    public ProviderOutboxScheduler(
            HandoffCache handoffCache,
            Vertx vertx,
            MeterRegistry registry,
            LeaderElection leaderElection,
            Instance<SwimProviderEventStorePort> eventStores,
            @ConfigProperty(name = "swim.outbox.recovery.batch-size", defaultValue = "100") int recoveryBatchSize,
            @ConfigProperty(name = "swim.outbox.recovery.max-retries", defaultValue = "5") int maxRetries,
            @ConfigProperty(name = "swim.provider.outbox.event-address") String outboxEventAddress,
            @ConfigProperty(name = "swim.provider.metric-prefix") String metricPrefix) {
        super(handoffCache, vertx, registry, leaderElection, recoveryBatchSize, maxRetries);
        this.eventStores = eventStores;
        this.outboxEventAddress = outboxEventAddress;
        this.metricPrefix = metricPrefix;
    }

    @Scheduled(every = "${swim.outbox.recovery.interval:30s}", delayed = "${swim.scheduler.initial-delay:30s}")
    @Transactional
    void scheduledRecovery() {
        recoverOrphanedEvents();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<SwimProviderEvent> findPendingEvents(int batchSize) {
        return (List<SwimProviderEvent>) eventStores.get().findPendingDelivery(batchSize);
    }

    @Override
    protected String getOutboxEventAddress() {
        return outboxEventAddress;
    }

    @Override
    protected String getMetricPrefix() {
        return metricPrefix;
    }
}
