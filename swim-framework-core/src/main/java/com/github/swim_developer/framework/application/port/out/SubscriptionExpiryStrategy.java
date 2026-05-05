package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.SubscriptionExpiry;

import java.time.Instant;
import java.util.List;

/**
 * SPI for subscription expiration and cleanup (provider-side).
 *
 * <p>Subscriptions have absolute expiration time ({@code subscriptionEnd} field).
 * Expiry process has two phases:
 * <ol>
 *   <li><b>TERMINATE</b>: Stop delivery, update status to TERMINATED (immediate)</li>
 *   <li><b>PURGE</b>: Delete queue, secrets, database record (after delay, e.g., 24h)</li>
 * </ol>
 *
 * <p><b>Why delayed purge?</b> Allows operators to inspect terminated subscriptions
 * for troubleshooting before physical deletion.</p>
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamExpiryStrategy} - Terminates + purges DNOTAM subscriptions</li>
 *   <li>{@code Ed254ExpiryStrategy} - Terminates + purges ED-254 subscriptions</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamExpiryStrategy implements SubscriptionExpiryStrategy {
 *     @Inject DnotamSubscriptionRepository repository;
 *     @Inject ArtemisJmxQueueProvisioner queueProvisioner;
 *
 *     @Override
 *     public List<SubscriptionExpiry> findExpiredSubscriptions(Instant now) {
 *         return repository.findAll()
 *             .filter(s -> s.subscriptionEnd.isBefore(now))
 *             .filter(s -> "ACTIVE".equals(s.status))
 *             .map(s -> new SubscriptionExpiry(s.subscriptionId, s.queueName, s.subscriptionEnd))
 *             .toList();
 *     }
 *
 *     @Override
 *     public void terminateSubscription(String subscriptionId) {
 *         repository.updateStatus(subscriptionId, "TERMINATED");
 *         // Stop delivery but keep queue for purge delay
 *     }
 *
 *     @Override
 *     public void purgeSubscription(String subscriptionId) {
 *         var sub = repository.findBySubscriptionId(subscriptionId).orElseThrow();
 *         queueProvisioner.removeQueue(sub.queueName);
 *         repository.deleteBySubscriptionId(subscriptionId);
 *     }
 * }
 * }</pre>
 *
 * @see com.github.swim_developer.framework.provider.application.subscription.SubscriptionExpiryScheduler
 */
public interface SubscriptionExpiryStrategy {

    /**
     * Finds subscriptions that have expired (subscriptionEnd before now).
     * Only ACTIVE subscriptions should be returned (TERMINATED already handled).
     *
     * @param now current timestamp
     * @return expired subscriptions to terminate
     */
    List<SubscriptionExpiry> findExpiredSubscriptions(Instant now);

    /**
     * Finds TERMINATED subscriptions ready for purge (terminated before threshold).
     * Typical threshold: now - 24 hours.
     *
     * @param threshold purge threshold (subscriptions terminated before this time)
     * @return terminated subscriptions to purge
     */
    List<SubscriptionExpiry> findTerminatedSubscriptionsToPurge(Instant threshold);

    /**
     * Terminates expired subscription. Updates status to TERMINATED, stops delivery.
     * Queue and database record remain for purge delay.
     *
     * @param subscriptionId subscription to terminate
     */
    void terminateSubscription(String subscriptionId);

    /**
     * Purges terminated subscription. Deletes queue, secrets, database record.
     *
     * @param subscriptionId subscription to purge
     */
    void purgeSubscription(String subscriptionId);

}
