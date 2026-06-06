package com.github.swim_developer.framework.application.port.in;

import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;

import java.util.List;

/**
 * SPI for reading and processing messages from the consumer inbox.
 *
 * <p>Extension modules provide abstract implementations of this interface for specific
 * transports (Kafka, AMQP/Artemis, etc.). Application modules provide the concrete
 * class that binds the channel and delegates business processing.
 *
 * <p><b>Example usage (Kafka):</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamInboxMessageHandler extends AbstractKafkaInboxReader {
 *
 *     @Incoming("in-dnotam-inbox")
 *     @Blocking
 *     public CompletionStage<Void> onInboxBatch(KafkaRecordBatch<String, String> batch) {
 *         List<PreparedEvent<EventData>> prepared = prepareBatch(batch, processor.eventProcessingOrchestrator());
 *         if (!prepared.isEmpty()) {
 *             processor.batchPersistAndDispatch(prepared);
 *             processor.markBatchAsProcessed(prepared);
 *         }
 *         processedCounter.increment(prepared.size());
 *         return batch.ack();
 *     }
 *
 *     @Override
 *     protected void processSingleMessage(InboxEnvelope envelope, String xmlPayload, int index) { ... }
 *
 *     @Override
 *     protected String getMetricPrefix() { return "dnotam"; }
 * }
 * }</pre>
 *
 * <p><b>Switching transports:</b> To switch from Kafka to Artemis, replace
 * {@code extends AbstractKafkaInboxReader} with {@code extends AbstractArtemisInboxReader}
 * (from the corresponding extension module) and update the {@code @Incoming} method signature.
 */
public interface SwimInboxReader {

    /**
     * Processes a single deserialized message from the inbox.
     * Called by the abstract base class for each individual message within an envelope.
     *
     * @param envelope   the inbox envelope containing routing metadata
     * @param xmlPayload the individual XML payload to process
     * @param index      zero-based index within the envelope batch
     */
    void processSingleMessage(InboxEnvelope envelope, String xmlPayload, int index);

    /**
     * Returns the metric prefix used for Micrometer counters.
     * Example: {@code "dnotam"} produces {@code dnotam_inbox_processed_total}.
     */
    String getMetricPrefix();

    /**
     * Splits the raw AMQP payload into individual messages.
     * Override to handle multi-message envelopes (e.g., AIXM Basic Message with multiple members).
     * Default implementation returns the payload as a single-element list.
     *
     * @param rawPayload the raw AMQP message payload
     * @return list of individual message payloads
     */
    default List<String> extractMessages(String rawPayload) {
        return List.of(rawPayload);
    }
}
