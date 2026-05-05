package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.SubscriptionRenewalInfo;
import com.github.swim_developer.framework.domain.exception.SubscriptionRenewalException;

import java.time.Instant;
import java.util.List;

/**
 * SPI for automatic subscription renewal (consumer-side).
 *
 * <p>Subscriptions have absolute expiration time ({@code subscriptionEnd}).
 * Consumer automatically renews subscriptions before expiry to maintain continuous service.
 * Typical threshold: 1 hour before expiration.</p>
 *
 * <p><b>Renewal process:</b>
 * <ol>
 *   <li>Find subscriptions with {@code subscriptionEnd < now + threshold}</li>
 *   <li>Call provider REST API: {@code PUT /subscriptions/{id}/renew}</li>
 *   <li>Update local database with new {@code subscriptionEnd}</li>
 *   <li>Retry with exponential backoff on failure (3 attempts, max 30s)</li>
 * </ol>
 *
 * <p><b>404/410 recovery:</b> If renewal returns 404 (Not Found) or 410 (Gone),
 * subscription was deleted on provider side. Consumer calls {@link #onSubscriptionLost}
 * to mark INVALID, delete local record, and optionally re-subscribe if desired config known.</p>
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamRenewalStrategy} - Renews DNOTAM subscriptions via REST API</li>
 *   <li>{@code Ed254RenewalStrategy} - Renews ED-254 subscriptions via REST API</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamRenewalStrategy implements SubscriptionRenewalStrategy {
 *     @Inject DnotamSubscriptionRepository repository;
 *     @Inject AbstractSubscriptionManagerClientRegistry smClientRegistry;
 *
 *     @Override
 *     public List<SubscriptionRenewalInfo> findSubscriptionsNearExpiry(Instant threshold) {
 *         return repository.findBySubscriptionEndBefore(threshold)
 *             .stream()
 *             .filter(s -> "ACTIVE".equals(s.status))
 *             .map(s -> new SubscriptionRenewalInfo(s.subscriptionId, s.subscriptionEnd))
 *             .toList();
 *     }
 *
 *     @Override
 *     public void renewSubscription(String subscriptionId) {
 *         var client = smClientRegistry.getClient("provider-id");
 *         var response = client.renewSubscription(subscriptionId);
 *         repository.updateSubscriptionEnd(subscriptionId, response.newEnd);
 *     }
 *
 *     @Override
 *     public void onSubscriptionLost(String subscriptionId) {
 *         repository.updateStatus(subscriptionId, "INVALID");
 *         repository.deleteBySubscriptionId(subscriptionId);
 *         // Optionally re-subscribe based on desired config
 *     }
 * }
 * }</pre>
 *
 * @see com.github.swim_developer.framework.consumer.application.subscription.schedule.SubscriptionRenewalScheduler
 */
public interface SubscriptionRenewalStrategy {

    /**
     * Finds subscriptions nearing expiration that require renewal.
     * Typical threshold: now + 1 hour.
     *
     * @param threshold expiration threshold (subscriptions expiring before this time)
     * @return subscriptions to renew
     */
    List<SubscriptionRenewalInfo> findSubscriptionsNearExpiry(Instant threshold);

    /**
     * Renews subscription by calling provider REST API and updating local database.
     * Implementation should handle 404/410 by calling {@link #onSubscriptionLost}.
     *
     * @param subscriptionId subscription to renew
     * @throws SubscriptionRenewalException if renewal fails (retryable)
     */
    void renewSubscription(String subscriptionId) throws SubscriptionRenewalException;

    /**
     * Handles subscription lost on provider (404/410 response).
     * Default implementation does nothing. Override to implement recovery logic:
     * mark INVALID, delete local record, optionally re-subscribe.
     *
     * @param subscriptionId lost subscription
     */
    default void onSubscriptionLost(String subscriptionId) {
    }

}
