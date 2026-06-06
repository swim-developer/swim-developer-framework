package com.github.swim_developer.framework.application.port.out;

/**
 * SPI for routing validated events from outbox to external systems.
 * Typically routes to Kafka topics based on business intent.
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamKafkaOutboxRouter} - Routes DNOTAM events to 6 Kafka topics by scenario</li>
 *   <li>{@code Ed254KafkaOutboxRouter} - Routes ED-254 arrivals to single Kafka topic</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamKafkaOutboxRouter extends AbstractKafkaOutboxRouter {
 *     @Override
 *     protected String determineTopicForEvent(String payload) {
 *         if (payload.contains("RWY.CLS")) return "dnotam-events-closure-topic";
 *         if (payload.contains("RWY.LIM")) return "dnotam-events-restriction-topic";
 *         // ... business intent routing
 *         return "dnotam-events-others-topic";
 *     }
 * }
 * }</pre>
 *
 * <p><b>Note:</b> Implementation must be idempotent - same messageId may be routed multiple times.
 */
public interface SwimOutboxRouter {

    /**
     * Routes validated event to destination (Kafka, AMQP, file, etc.).
     * Implementation determines routing logic (topic selection, partitioning).
     *
     * @param messageId unique message identifier for idempotency
     * @param payload validated event payload (XML, JSON)
     */
    void route(String messageId, String payload);

    /**
     * Sends unroutable or failed events to dead letter queue.
     *
     * @param messageId message identifier
     * @param payload event payload
     */
    void sendToDeadLetterQueue(String messageId, String payload);
}
