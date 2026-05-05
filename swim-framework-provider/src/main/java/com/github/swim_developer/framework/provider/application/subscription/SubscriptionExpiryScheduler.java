package com.github.swim_developer.framework.provider.application.subscription;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.application.port.out.SubscriptionExpiryStrategy;
import com.github.swim_developer.framework.domain.model.SubscriptionExpiry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


@Slf4j
@ApplicationScoped
public class SubscriptionExpiryScheduler {

    private final Duration purgeDelay;
    private final Instance<SubscriptionExpiryStrategy> strategyInstance;
    private final MeterRegistry meterRegistry;
    private final LeaderElection leaderElection;

    private Counter subscriptionsTerminatedCounter;
    private Counter subscriptionsPurgedCounter;

    @Inject
    public SubscriptionExpiryScheduler(
            @ConfigProperty(name = "swim.subscription.expiry.purge-delay", defaultValue = "24h") Duration purgeDelay,
            Instance<SubscriptionExpiryStrategy> strategyInstance,
            MeterRegistry meterRegistry,
            LeaderElection leaderElection) {
        this.purgeDelay = purgeDelay;
        this.strategyInstance = strategyInstance;
        this.meterRegistry = meterRegistry;
        this.leaderElection = leaderElection;
    }

    
    @PostConstruct
    void initMetrics() {
        subscriptionsTerminatedCounter = Counter.builder("swim_subscriptions_terminated_total")
                .description("Total subscriptions terminated due to expiration")
                .register(meterRegistry);

        subscriptionsPurgedCounter = Counter.builder("swim_subscriptions_purged_total")
                .description("Total subscriptions purged (deleted)")
                .register(meterRegistry);

        if (!strategyInstance.isResolvable()) {
            log.warn("No SubscriptionExpiryStrategy implementation found - expiry disabled");
        } else {
            log.info("SWIM Subscription Expiry Scheduler initialized - Purge delay: {}", purgeDelay);
        }
    }

    
    @Scheduled(every = "${swim.subscription.expiry.check-interval:60s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkExpiredSubscriptions() {
        if (!strategyInstance.isResolvable()) {
            return;
        }

        if (!leaderElection.isLeader()) {
            return;
        }

        SubscriptionExpiryStrategy strategy = strategyInstance.get();
        Instant now = Instant.now();
        List<SubscriptionExpiry> expired = strategy.findExpiredSubscriptions(now);

        if (expired.isEmpty()) {
            log.trace("No expired subscriptions found");
            return;
        }

        log.info("Found {} expired subscription(s) to terminate", expired.size());

        int terminated = 0;
        int failed = 0;

        for (SubscriptionExpiry subscription : expired) {
            try {
                strategy.terminateSubscription(subscription.subscriptionId());
                subscriptionsTerminatedCounter.increment();
                terminated++;

                log.info("Subscription terminated due to expiration - ID: {}, SubscriptionEnd: {}, Status: {}",
                        subscription.subscriptionId(),
                        subscription.subscriptionEnd(),
                        subscription.currentStatus());
            } catch (Exception e) {
                log.error("Failed to terminate expired subscription: {}", subscription.subscriptionId(), e);
                failed++;
            }
        }

        log.info("Expiry check completed - Terminated: {}, Failed: {}", terminated, failed);
    }

    
    @Scheduled(every = "${swim.subscription.expiry.purge-interval:5m}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void purgeTerminatedSubscriptions() {
        if (!strategyInstance.isResolvable()) {
            return;
        }

        if (!leaderElection.isLeader()) {
            return;
        }

        SubscriptionExpiryStrategy strategy = strategyInstance.get();
        Instant threshold = Instant.now().minus(purgeDelay);
        List<SubscriptionExpiry> toPurge = strategy.findTerminatedSubscriptionsToPurge(threshold);

        if (toPurge.isEmpty()) {
            log.trace("No terminated subscriptions to purge");
            return;
        }

        log.info("Found {} terminated subscription(s) to purge", toPurge.size());

        int purged = 0;
        int failed = 0;

        for (SubscriptionExpiry subscription : toPurge) {
            try {
                strategy.purgeSubscription(subscription.subscriptionId());
                subscriptionsPurgedCounter.increment();
                purged++;

                log.info("Subscription purged - ID: {}, SubscriptionEnd: {}, Status: {}",
                        subscription.subscriptionId(),
                        subscription.subscriptionEnd(),
                        subscription.currentStatus());
            } catch (Exception e) {
                log.error("Failed to purge subscription: {}", subscription.subscriptionId(), e);
                failed++;
            }
        }

        log.info("Purge completed - Purged: {}, Failed: {}", purged, failed);
    }
}
