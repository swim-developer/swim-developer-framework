package com.github.swim_developer.framework.consumer.infrastructure.in.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.framework.application.port.out.SubscriptionHeartbeatPublisher;
import com.github.swim_developer.framework.application.port.out.SwimInboxStore;
import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.amqp.AmqpMessage;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class AbstractAmqpConsumerManager implements com.github.swim_developer.framework.application.port.out.SwimConsumerManagerPort {

    private final Vertx vertx;
    private final MeterRegistry meterRegistry;
    private final AmqpConnectionRegistry connectionRegistry;
    private final SubscriptionHeartbeatTracker heartbeatTracker;
    private final ObjectMapper objectMapper;
    private final SwimInboxStore inboxStore;

    @ConfigProperty(name = "swim.service.name", defaultValue = "swim")
    String serviceName;

    @ConfigProperty(name = "swim.inbox.batch.size", defaultValue = "50")
    int inboxBatchSize;

    @ConfigProperty(name = "swim.inbox.batch.flush-interval-ms", defaultValue = "50")
    long inboxBatchFlushIntervalMs;

    private InboxBatchProcessor batchProcessor;
    private AmqpConsumerLifecycleManager lifecycleManager;

    protected AbstractAmqpConsumerManager() {
        this(null, null, null, null, null, null);
    }

    @Inject
    public AbstractAmqpConsumerManager(
            Vertx vertx,
            MeterRegistry meterRegistry,
            AmqpConnectionRegistry connectionRegistry,
            SubscriptionHeartbeatTracker heartbeatTracker,
            ObjectMapper objectMapper,
            SwimInboxStore inboxStore) {
        this.vertx = vertx;
        this.meterRegistry = meterRegistry;
        this.connectionRegistry = connectionRegistry;
        this.heartbeatTracker = heartbeatTracker;
        this.objectMapper = objectMapper;
        this.inboxStore = inboxStore;
    }

    @PostConstruct
    void init() {
        batchProcessor = new InboxBatchProcessor(
            vertx,
            meterRegistry,
            inboxStore,
            getMetricPrefix(),
            inboxBatchSize,
            inboxBatchFlushIntervalMs
        );
        batchProcessor.init();

        lifecycleManager = new AmqpConsumerLifecycleManager(
            vertx,
            connectionRegistry,
            (subscriptionId, providerId) -> heartbeatTracker.remove(subscriptionId)
        );
    }

    public void registerConsumer(String subscriptionId, String queueName, ProviderConfiguration provider) {
        lifecycleManager.register(
            subscriptionId,
            queueName,
            provider,
            msg -> handleIncomingMessage(subscriptionId, queueName, msg),
            amqpClient -> heartbeatTracker.registerSubscription(subscriptionId)
        );
    }

    protected void handleIncomingMessage(String subscriptionId, String queueName, AmqpMessage message) {
        String contentType = message.contentType();

        if (SubscriptionHeartbeatPublisher.HEARTBEAT_CONTENT_TYPE.equals(contentType)) {
            handleHeartbeat(subscriptionId, message);
            return;
        }

        String messageId = message.id();
        String payload = message.bodyAsString();
        log.debug("AMQP message received - MessageId: {}, Queue: {}", messageId, queueName);

        InboxEnvelope envelope = new InboxEnvelope(subscriptionId, queueName, messageId, payload, contentType, Instant.now());
        batchProcessor.enqueue(envelope, message);
    }

    private void handleHeartbeat(String subscriptionId, AmqpMessage message) {
        try {
            SubscriptionHeartbeat heartbeat = objectMapper.readValue(
                    message.bodyAsString(), SubscriptionHeartbeat.class);
            heartbeatTracker.recordHeartbeat(subscriptionId, heartbeat);
            log.trace("Heartbeat received - SubId: {}, seq: {}", subscriptionId, heartbeat.sequenceNumber());
        } catch (Exception e) {
            log.warn("Failed to parse heartbeat for subscription {}: {}", subscriptionId, e.getMessage());
        }
    }

    public void pauseConsumer(String subscriptionId) {
        lifecycleManager.pause(subscriptionId);
    }

    public void unregisterConsumer(String subscriptionId) {
        lifecycleManager.unregister(subscriptionId);
        heartbeatTracker.remove(subscriptionId);
    }

    public boolean isConnected() {
        return lifecycleManager.isConnected();
    }

    public int getActiveConsumerCount() {
        return lifecycleManager.getActiveCount();
    }

    public String getConnectedProviders() {
        return lifecycleManager.getConnectedProviders();
    }

    public boolean hasZombieConsumers() {
        return lifecycleManager.hasZombieConsumers();
    }

    public void resetClient() {
        log.info("Resetting all AMQP connections for reconnection");
        Set<String> providerIds = lifecycleManager.getProviderIds();
        lifecycleManager.unregisterAll();
        providerIds.forEach(providerId -> {
            log.info("Evicting stale AMQP client from registry for provider '{}'", providerId);
            connectionRegistry.resetProvider(providerId);
        });
    }

    @PreDestroy
    protected void cleanup() {
        batchProcessor.cleanup();
        lifecycleManager.unregisterAll();
    }

    protected String getMetricPrefix() {
        return serviceName;
    }
}
