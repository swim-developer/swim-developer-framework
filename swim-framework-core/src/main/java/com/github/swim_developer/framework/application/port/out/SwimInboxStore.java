package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;

import java.util.concurrent.CompletionStage;

/**
 * SPI for persisting incoming AMQP messages to durable storage immediately after receipt.
 * The framework ACKs the AMQP broker only after store() completes successfully.
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamKafkaInboxStore} - Persists DNOTAM envelopes to a Kafka inbox topic</li>
 *   <li>{@code Ed254KafkaInboxStore} - Persists ED-254 envelopes to a Kafka inbox topic</li>
 * </ul>
 *
 * <p><b>Note:</b> Exactly one implementation must be present on the classpath per consumer application.
 * Add the appropriate extension module as a Maven dependency to select the implementation.
 */
public interface SwimInboxStore {

    /**
     * Persists the incoming AMQP message envelope to durable storage.
     * Called before the AMQP ACK is sent to the broker.
     *
     * @param envelope the received message envelope containing subscriptionId, queueName,
     *                 messageId, raw payload and receivedAt timestamp
     * @return a CompletionStage that completes when the message is durably stored
     */
    CompletionStage<Void> store(InboxEnvelope envelope);
}
