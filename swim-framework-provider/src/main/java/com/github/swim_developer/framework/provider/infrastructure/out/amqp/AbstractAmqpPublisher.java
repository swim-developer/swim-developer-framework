package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

import com.github.swim_developer.framework.domain.model.QualityOfService;
import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Slf4j
public abstract class AbstractAmqpPublisher implements com.github.swim_developer.framework.application.port.out.SwimAmqpPublisherPort {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    public void publish(String address, String payload, QualityOfService qos) {
        publish(address, payload, qos, getContentType());
    }

    public void publish(String address, String payload, QualityOfService qos, String contentType) {
        log.debug("Publishing to address: {} with QoS: {}, content-type: {}", address, qos, contentType);

        boolean durable = mapQosToDurable(qos);
        String messageId = UUID.randomUUID().toString();

        OutgoingAmqpMetadata metadata = OutgoingAmqpMetadata.builder()
                .withAddress(address)
                .withDurable(durable)
                .withSubject(address)
                .withMessageId(messageId)
                .withCreationTime(Instant.now().toEpochMilli())
                .withContentType(contentType)
                .build();

        CompletableFuture<Void> ackFuture = new CompletableFuture<>();
        Message<String> message = Message.of(payload,
                () -> { ackFuture.complete(null); return CompletableFuture.completedFuture(null); },
                reason -> { ackFuture.completeExceptionally(reason); return CompletableFuture.completedFuture(null); }
        ).addMetadata(metadata);

        try {
            getEmitter().send(message);
            ackFuture.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpPublishException("AMQP publish interrupted - address: " + address, e);
        } catch (Exception e) {
            throw new AmqpPublishException("AMQP publish failed - address: " + address, e);
        }
    }

    public void publishToQueue(String queue, String payload, QualityOfService qos, UUID subscriptionId) {
        log.debug("Publishing to queue: {} for subscription: {} with QoS: {}", queue, subscriptionId, qos);

        Optional<PerQueueCircuitBreaker> cb = getCircuitBreaker();
        cb.filter(c -> c.isOpen(queue)).ifPresent(c -> {
            throw new CircuitBreakerOpenException(queue);
        });

        if (qos == QualityOfService.EXACTLY_ONCE) {
            log.warn("AUDIT: QoS EXACTLY_ONCE requested - address: {}, subscription: {}", queue, subscriptionId);
        }

        try {
            publish(queue, payload, qos);
            cb.ifPresent(c -> c.recordSuccess(queue));
        } catch (Exception e) {
            cb.ifPresent(c -> c.recordFailure(queue));
            throw e;
        }
    }

    protected Optional<PerQueueCircuitBreaker> getCircuitBreaker() {
        return Optional.empty();
    }

    private boolean mapQosToDurable(QualityOfService qos) {
        return switch (qos) {
            case AT_MOST_ONCE -> false;
            case AT_LEAST_ONCE -> true;
            case EXACTLY_ONCE -> true;
        };
    }

    public boolean isHealthy() {
        try {
            Emitter<String> emitter = getEmitter();
            return emitter != null && !emitter.isCancelled();
        } catch (Exception e) {
            log.error("AMQP health check failed", e);
            return false;
        }
    }

    protected String getContentType() {
        return "application/xml";
    }

    protected abstract Emitter<String> getEmitter();
}
