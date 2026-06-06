package com.github.swim_developer.framework.application.port.in;

/**
 * SPI for event ingestion from external sources. Provider-side extension point (EP-4).
 *
 * <p>Implemented by provider applications to receive events from pluggable sources
 * (Kafka, AMQP, file, database). Extensions inject this SPI and call
 * {@link #processEvent(String)} when a new payload arrives.</p>
 *
 * <p><b>Relation to consumer:</b> Mirror of {@link SwimOutboxRouter}.
 * Consumer uses SwimOutboxRouter to dispatch events OUT (to Kafka).
 * Provider uses SwimIngressHandler to ingest events IN (from Kafka).</p>
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamKafkaIngress} - Consumes DNOTAM events from Kafka topic</li>
 *   <li>{@code Ed254KafkaIngress} - Consumes ED-254 arrivals from external system</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamKafkaIngress {
 *     @Inject SwimIngressHandler ingressHandler;
 *
 *     @Incoming("dnotam-events-topic")
 *     public void consume(String payload) {
 *         ingressHandler.processEvent(payload);  // Triggers provider outbox
 *     }
 * }
 * }</pre>
 */
public interface SwimIngressHandler {

    /**
     * Processes incoming event payload. Provider validates, persists to outbox,
     * and delivers to active subscriptions.
     *
     * @param payload event payload (XML, JSON)
     */
    void processEvent(String payload);
}
