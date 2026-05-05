package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.ActiveSubscriptionInfo;

import java.util.List;

/**
 * SPI for retrieving active subscriptions for per-subscription heartbeat publishing (provider-side).
 *
 * <p>Invoked by {@code PerSubscriptionHeartbeatScheduler} every 15 seconds to determine
 * which subscriptions should receive heartbeats. Both ACTIVE and PAUSED subscriptions
 * receive heartbeats (PAUSED keeps connection alive while business processing is disabled).</p>
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>{@code DnotamActiveSubscriptionSupplier} - Queries PostgreSQL for ACTIVE/PAUSED subscriptions</li>
 *   <li>{@code Ed254ActiveSubscriptionSupplier} - Queries PostgreSQL for ACTIVE/PAUSED subscriptions</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class DnotamActiveSubscriptionSupplier implements ActiveSubscriptionSupplier {
 *     @Inject DnotamSubscriptionRepository repository;
 *
 *     @Override
 *     public List<ActiveSubscriptionInfo> getActiveSubscriptions() {
 *         return repository.findAll()
 *             .filter(s -> "ACTIVE".equals(s.status) || "PAUSED".equals(s.status))
 *             .map(s -> new ActiveSubscriptionInfo(s.subscriptionId, s.queueName))
 *             .toList();
 *     }
 * }
 * }</pre>
 *
 * @see SubscriptionHeartbeatPublisher
 * @see com.github.swim_developer.framework.provider.application.heartbeat.PerSubscriptionHeartbeatScheduler
 */
public interface ActiveSubscriptionSupplier {

    /**
     * Retrieves all subscriptions eligible for heartbeat publishing.
     * Should include both ACTIVE and PAUSED subscriptions.
     * INVALID, TERMINATED, or deleted subscriptions must be excluded.
     *
     * @return list of active subscription metadata (subscriptionId, queueName)
     */
    List<ActiveSubscriptionInfo> getActiveSubscriptions();
}
