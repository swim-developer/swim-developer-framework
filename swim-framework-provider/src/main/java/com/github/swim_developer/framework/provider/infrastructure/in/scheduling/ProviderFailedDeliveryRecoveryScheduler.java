package com.github.swim_developer.framework.provider.infrastructure.in.scheduling;

import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.application.port.out.SwimAmqpPublisherPort;
import com.github.swim_developer.framework.application.port.out.SwimFailedDeliveryStorePort;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.provider.application.messaging.AbstractFailedDeliveryRecoveryScheduler;
import com.github.swim_developer.framework.provider.application.port.out.SwimProviderEventStorePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

@Slf4j
@ApplicationScoped
public class ProviderFailedDeliveryRecoveryScheduler
        extends AbstractFailedDeliveryRecoveryScheduler<SwimProviderEvent, SwimFailedDelivery> {

    private final Instance<SwimFailedDeliveryStorePort> failedDeliveryStores;
    private final Instance<SwimProviderEventStorePort> eventStores;
    private final Instance<SwimAmqpPublisherPort> amqpPublishers;
    private final MeterRegistry registry;
    private final String metricPrefix;

    protected ProviderFailedDeliveryRecoveryScheduler() {
        this.failedDeliveryStores = null;
        this.eventStores = null;
        this.amqpPublishers = null;
        this.registry = null;
        this.metricPrefix = null;
    }

    @Inject
    public ProviderFailedDeliveryRecoveryScheduler(
            LeaderElection leaderElection,
            Instance<SwimFailedDeliveryStorePort> failedDeliveryStores,
            Instance<SwimProviderEventStorePort> eventStores,
            Instance<SwimAmqpPublisherPort> amqpPublishers,
            MeterRegistry registry,
            @ConfigProperty(name = "swim.failed-delivery.recovery.max-retries", defaultValue = "5") int maxRetries,
            @ConfigProperty(name = "swim.failed-delivery.recovery.batch-size", defaultValue = "50") int batchSize,
            @ConfigProperty(name = "swim.provider.metric-prefix") String metricPrefix) {
        super(leaderElection, maxRetries, batchSize);
        this.failedDeliveryStores = failedDeliveryStores;
        this.eventStores = eventStores;
        this.amqpPublishers = amqpPublishers;
        this.registry = registry;
        this.metricPrefix = metricPrefix;
    }

    @Scheduled(every = "${swim.failed-delivery.recovery.interval:30s}",
            delayed = "${swim.scheduler.initial-delay:30s}")
    @Transactional
    void scheduledRecovery() {
        executeRecovery();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected FailedDeliveryStore<SwimFailedDelivery> getStore() {
        return (FailedDeliveryStore<SwimFailedDelivery>) failedDeliveryStores.get();
    }

    @Override
    protected SwimProviderEvent loadEvent(String eventId) {
        return eventStores.get().findDomainById(eventId);
    }

    @Override
    protected void republish(String queue, String payload, QualityOfService qos, UUID subscriptionId) {
        amqpPublishers.get().publishToQueue(queue, payload, qos, subscriptionId);
    }

    @Override
    protected void onDeliveryRecovered(SwimFailedDelivery fd) {
        Counter.builder(metricPrefix + "_failed_delivery_recovered_total")
                .description("Total failed deliveries successfully recovered")
                .register(registry).increment();
    }

    @Override
    protected void onDeliveryDeadLettered(SwimFailedDelivery fd) {
        Counter.builder(metricPrefix + "_failed_delivery_deadletter_total")
                .description("Total failed deliveries that exceeded max retries")
                .register(registry).increment();
    }
}
