package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import java.util.List;
import java.util.UUID;

/**
 * SPI for tracking failed event deliveries and retry attempts (provider-side).
 *
 * <p>When AMQP delivery fails (queue full, broker down, circuit breaker open),
 * the provider persists failure record for scheduled retry. After max retries exceeded,
 * event moves to dead letter queue for manual inspection.</p>
 *
 * <p><b>Retry strategy:</b>
 * <ul>
 *   <li>Retry 1: immediate (0s delay)</li>
 *   <li>Retry 2: 30s delay</li>
 *   <li>Retry 3: 60s delay</li>
 *   <li>Max retries: 3 (configurable)</li>
 *   <li>After max: dead letter queue</li>
 * </ul>
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamFailedDeliveryStore} - PostgreSQL persistence with Panache</li>
 *   <li>{@code Ed254FailedDeliveryStore} - PostgreSQL persistence with Panache</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamFailedDeliveryStore
 *     implements FailedDeliveryStore<DnotamFailedDeliveryRecord> {
 *
 *     @Override
 *     public void persist(DnotamFailedDeliveryRecord record) {
 *         record.persist();
 *     }
 *
 *     @Override
 *     public DnotamFailedDeliveryRecord createRecord(
 *             String eventId, UUID subscriptionId, String queue, String errorMessage) {
 *         return DnotamFailedDeliveryRecord.builder()
 *             .eventId(eventId)
 *             .subscriptionId(subscriptionId)
 *             .queue(queue)
 *             .errorMessage(errorMessage)
 *             .retryCount(0)
 *             .build();
 *     }
 *
 *     @Override
 *     public List<DnotamFailedDeliveryRecord> findPendingRetries(int maxRetries, int batchSize) {
 *         return list("retryCount < ?1 ORDER BY createdAt ASC", maxRetries)
 *             .stream().limit(batchSize).toList();
 *     }
 * }
 * }</pre>
 *
 * @param <F> failed delivery record type extending {@link SwimFailedDelivery}
 * @see com.github.swim_developer.framework.provider.application.messaging.AbstractFailedDeliveryRecoveryScheduler
 */
public interface FailedDeliveryStore<F extends SwimFailedDelivery> extends SwimFailedDeliveryStorePort {

    /**
     * Persists or updates failed delivery record.
     *
     * @param deliveryRecord record to persist
     */
    void persist(F deliveryRecord);

    /**
     * Creates new failed delivery record for initial failure.
     *
     * @param eventId event identifier from outbox
     * @param subscriptionId subscription UUID
     * @param queue AMQP queue name
     * @param errorMessage failure reason
     * @return new record with retryCount=0
     */
    F createRecord(String eventId, UUID subscriptionId, String queue, String errorMessage);

    /**
     * Finds failed deliveries eligible for retry (retryCount &lt; maxRetries).
     *
     * @param maxRetries maximum retry attempts before dead letter
     * @param batchSize maximum records to return
     * @return pending retries ordered by creation time
     */
    List<F> findPendingRetries(int maxRetries, int batchSize);

    /**
     * Finds failed deliveries that exceeded max retries (for dead letter queue).
     *
     * @param maxRetries retry threshold
     * @param batchSize maximum records to return
     * @return exhausted retries
     */
    List<F> findExceededRetries(int maxRetries, int batchSize);

    /**
     * Counts pending retries for specific event (used for idempotency check).
     *
     * @param eventId event identifier
     * @return number of pending retries
     */
    long countPendingByEventId(String eventId);
}
