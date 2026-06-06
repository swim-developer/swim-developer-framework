package com.github.swim_developer.framework.consumer.infrastructure.in.amqp;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpMessage;
import io.vertx.amqp.AmqpReceiver;
import io.vertx.amqp.AmqpReceiverOptions;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
public class AmqpConsumerLifecycleManager {

    private final Vertx vertx;
    private final AmqpConnectionRegistry connectionRegistry;
    private final BiConsumer<String, String> onConsumerClosed;

    @ConfigProperty(name = "swim.amqp.max-buffered-messages", defaultValue = "100")
    int maxBufferedMessages;

    private final Map<String, AmqpReceiver> activeConsumers = new ConcurrentHashMap<>();
    private final Map<String, String> consumerProviderMap = new ConcurrentHashMap<>();
    private final Set<String> pendingRegistrations = ConcurrentHashMap.newKeySet();

    public AmqpConsumerLifecycleManager(Vertx vertx,
                                        AmqpConnectionRegistry connectionRegistry,
                                        BiConsumer<String, String> onConsumerClosed) {
        this.vertx = vertx;
        this.connectionRegistry = connectionRegistry;
        this.onConsumerClosed = onConsumerClosed;
    }

    public void register(String subscriptionId, String queueName, ProviderConfiguration provider,
                         java.util.function.Consumer<AmqpMessage> messageHandler,
                         java.util.function.Consumer<AmqpClient> onSuccess) {
        if (activeConsumers.containsKey(subscriptionId) || !pendingRegistrations.add(subscriptionId)) {
            log.warn("Consumer already registered or pending: {} - skipping", subscriptionId);
            return;
        }

        try {
            AmqpClient amqpClient = connectionRegistry.getOrCreate(provider, vertx);

            AmqpReceiverOptions receiverOptions = new AmqpReceiverOptions()
                    .setDurable(true)
                    .setAutoAcknowledgement(false)
                    .setMaxBufferedMessages(maxBufferedMessages)
                    .setLinkName("consumer-" + subscriptionId);

            amqpClient.createReceiver(queueName, receiverOptions)
                    .onSuccess(receiver -> {
                        pendingRegistrations.remove(subscriptionId);
                        activeConsumers.put(subscriptionId, receiver);
                        consumerProviderMap.put(subscriptionId, provider.providerId());
                        var amqp = provider.amqpBroker();
                        log.info("AMQP consumer connected - Provider: {}, Broker: {}:{}, Queue: {}, SubscriptionId: {}, MaxBuffered: {}",
                                provider.providerId(), amqp.host(), amqp.port(),
                                queueName, subscriptionId, maxBufferedMessages);

                        receiver.handler(messageHandler::accept);

                        receiver.exceptionHandler(err -> {
                            log.error("Consumer error - SubscriptionId: {}, Provider: {}",
                                    subscriptionId, provider.providerId(), err);
                            close(subscriptionId);
                        });

                        receiver.connection().exceptionHandler(err -> {
                            log.error("AMQP connection lost - Provider: {}, Broker: {}:{}",
                                    provider.providerId(), amqp.host(), amqp.port());
                            close(subscriptionId);
                        });

                        onSuccess.accept(amqpClient);
                    })
                    .onFailure(err -> {
                        pendingRegistrations.remove(subscriptionId);
                        log.error("Failed to create consumer: {}, Provider: {}",
                                subscriptionId, provider.providerId(), err);
                    });
        } catch (Exception e) {
            pendingRegistrations.remove(subscriptionId);
            log.error("Failed to register consumer: {}, Provider: {}", subscriptionId, provider.providerId(), e);
        }
    }

    public void pause(String subscriptionId) {
        AmqpReceiver receiver = activeConsumers.remove(subscriptionId);
        if (receiver != null) {
            receiver.close();
            log.info("Business consumer paused (heartbeat kept alive) - SubId: {}", subscriptionId);
        }
    }

    public void unregister(String subscriptionId) {
        AmqpReceiver receiver = activeConsumers.remove(subscriptionId);
        consumerProviderMap.remove(subscriptionId);
        if (receiver != null) {
            receiver.close();
        }
    }

    public void unregisterAll() {
        activeConsumers.forEach((subscriptionId, receiver) -> unregister(subscriptionId));
    }

    public boolean isConnected() {
        return !activeConsumers.isEmpty();
    }

    public int getActiveCount() {
        return activeConsumers.size();
    }

    public String getConnectedProviders() {
        return String.join(", ", consumerProviderMap.values().stream().distinct().sorted().toList());
    }

    public boolean hasZombieConsumers() {
        if (activeConsumers.isEmpty()) {
            return false;
        }
        return activeConsumers.values().stream()
                .anyMatch(receiver -> receiver.connection().isDisconnected());
    }

    public Set<String> getProviderIds() {
        return new java.util.HashSet<>(consumerProviderMap.values());
    }

    private void close(String subscriptionId) {
        AmqpReceiver receiver = activeConsumers.remove(subscriptionId);
        String providerId = consumerProviderMap.remove(subscriptionId);
        if (receiver != null) {
            try {
                receiver.close();
            } catch (Exception e) {
                log.debug("Error closing consumer receiver for {}: {}", subscriptionId, e.getMessage());
            }
        }
        if (onConsumerClosed != null && providerId != null) {
            onConsumerClosed.accept(subscriptionId, providerId);
        }
    }
}
