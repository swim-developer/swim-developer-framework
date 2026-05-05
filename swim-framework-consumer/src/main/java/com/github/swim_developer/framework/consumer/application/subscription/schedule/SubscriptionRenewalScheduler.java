package com.github.swim_developer.framework.consumer.application.subscription.schedule;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import com.github.swim_developer.framework.domain.exception.SubscriptionRenewalException;
import com.github.swim_developer.framework.application.port.out.SubscriptionRenewalStrategy;
import com.github.swim_developer.framework.domain.model.SubscriptionRenewalInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
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
public class SubscriptionRenewalScheduler {

    private final boolean renewalEnabled;
    private final Duration renewalThreshold;
    private final int retryCount;
    private final Instance<SubscriptionRenewalStrategy> strategyInstance;
    private final LeaderElection leaderElection;
    private final Counter subscriptionsRenewedCounter;
    private final Counter renewalFailuresCounter;

    @Inject
    public SubscriptionRenewalScheduler(
            @ConfigProperty(name = "swim.subscription.renewal.enabled", defaultValue = "true") boolean renewalEnabled,
            @ConfigProperty(name = "swim.subscription.renewal.threshold", defaultValue = "1h") Duration renewalThreshold,
            @ConfigProperty(name = "swim.subscription.renewal.retry-count", defaultValue = "3") int retryCount,
            Instance<SubscriptionRenewalStrategy> strategyInstance,
            MeterRegistry meterRegistry,
            LeaderElection leaderElection) {
        this.renewalEnabled = renewalEnabled;
        this.renewalThreshold = renewalThreshold;
        this.retryCount = retryCount;
        this.strategyInstance = strategyInstance;
        this.leaderElection = leaderElection;

        this.subscriptionsRenewedCounter = Counter.builder("swim_subscriptions_renewed_total")
                .description("Total subscriptions successfully renewed")
                .register(meterRegistry);
        this.renewalFailuresCounter = Counter.builder("swim_subscription_renewal_failures_total")
                .description("Total subscription renewal failures")
                .register(meterRegistry);

        if (!strategyInstance.isResolvable()) {
            log.warn("No SubscriptionRenewalStrategy implementation found - renewal disabled");
        } else {
            log.info("SWIM Subscription Renewal Scheduler initialized - Threshold: {}, Retry: {}",
                    renewalThreshold, retryCount);
        }
    }

    @Scheduled(every = "${swim.subscription.renewal.check-interval:5m}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkAndRenewSubscriptions() {
        if (!renewalEnabled || !strategyInstance.isResolvable()) {
            log.trace("Subscription renewal disabled or no strategy available");
            return;
        }

        if (!leaderElection.isLeader()) {
            return;
        }

        SubscriptionRenewalStrategy strategy = strategyInstance.get();
        Instant now = Instant.now();
        Instant threshold = now.plus(renewalThreshold);

        List<SubscriptionRenewalInfo> toRenew = strategy.findSubscriptionsNearExpiry(threshold);

        if (toRenew.isEmpty()) {
            log.trace("No subscriptions need renewal");
            return;
        }

        log.info("Found {} subscription(s) approaching expiration (threshold: {})",
                toRenew.size(), renewalThreshold);

        int renewed = 0;
        int failed = 0;

        for (SubscriptionRenewalInfo subscription : toRenew) {
            try {
                Duration timeUntilExpiry = Duration.between(now, subscription.subscriptionEnd());
                log.info("Renewing subscription {} (expires in: {})",
                        subscription.subscriptionId(), timeUntilExpiry);

                renewSubscriptionWithRetry(strategy, subscription.subscriptionId());

                subscriptionsRenewedCounter.increment();
                renewed++;

                log.info("Subscription renewed successfully: {}", subscription.subscriptionId());
            } catch (Exception e) {
                log.error("Failed to renew subscription after {} retries: {}",
                        retryCount, subscription.subscriptionId(), e);
                renewalFailuresCounter.increment();
                failed++;
            }
        }

        log.info("Renewal check completed - Renewed: {}, Failed: {}", renewed, failed);
    }

    private void renewSubscriptionWithRetry(SubscriptionRenewalStrategy strategy, String subscriptionId) throws SubscriptionRenewalException {
        SubscriptionRenewalException lastException = null;

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                strategy.renewSubscription(subscriptionId);
                return;
            } catch (SubscriptionNotFoundException e) {
                log.warn("Provider returned 404/410 during renewal of {}. Delegating recovery to strategy.", subscriptionId);
                strategy.onSubscriptionLost(subscriptionId);
                return;
            } catch (SubscriptionRenewalException e) {
                lastException = e;
                if (attempt < retryCount) {
                    long delayMs = calculateBackoffDelay(attempt);
                    log.warn("Renewal attempt {} failed for subscription {}, retrying in {}ms",
                            attempt, subscriptionId, delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SubscriptionRenewalException(subscriptionId, ie);
                    }
                }
            }
        }

        throw lastException;
    }

    public static long calculateBackoffDelay(int attempt) {
        return Math.min(1000L * (1L << (attempt - 1)), 30000L);
    }
}
