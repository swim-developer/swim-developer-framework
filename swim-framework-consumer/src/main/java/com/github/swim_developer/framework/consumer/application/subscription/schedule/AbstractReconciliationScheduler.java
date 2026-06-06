package com.github.swim_developer.framework.consumer.application.subscription.schedule;

import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionCountPort;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AbstractAmqpConsumerManager;
import com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription.SwimSubscriptionConfigParserPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.filter.SubscriptionFilterCache;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Slf4j
@ApplicationScoped
public class AbstractReconciliationScheduler {

    private final LeaderElection leaderElection;
    private final ReconciliationStateTracker stateManager;
    private final SubscriptionFilterCache filterCache;
    private final SwimIdempotencyPort idempotencyPort;
    private final AbstractAmqpConsumerManager consumerManager;
    private final int filterCacheRefreshIntervalCycles;
    private final boolean deleteAndRecreate;

    private final Instance<SwimSubscriptionConfigParserPort> configParsers;
    private final Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private final Instance<SwimSubscriptionCountPort> subscriptionRepositories;

    private int reconciliationCycleCount;

    protected AbstractReconciliationScheduler() {
        this.leaderElection = null;
        this.stateManager = null;
        this.filterCache = null;
        this.idempotencyPort = null;
        this.consumerManager = null;
        this.filterCacheRefreshIntervalCycles = 0;
        this.deleteAndRecreate = false;
        this.configParsers = null;
        this.subscriptionServices = null;
        this.subscriptionRepositories = null;
    }

    @Inject
    public AbstractReconciliationScheduler(
            LeaderElection leaderElection,
            ReconciliationStateTracker stateManager,
            SubscriptionFilterCache filterCache,
            SwimIdempotencyPort idempotencyPort,
            AbstractAmqpConsumerManager consumerManager,
            @ConfigProperty(name = "swim.subscription.filter-cache.refresh-interval-cycles", defaultValue = "0") int filterCacheRefreshIntervalCycles,
            @ConfigProperty(name = "swim.subscriptions.delete-and-recreate", defaultValue = "false") boolean deleteAndRecreate,
            Instance<SwimSubscriptionConfigParserPort> configParsers,
            Instance<SwimSubscriptionLifecyclePort> subscriptionServices,
            Instance<SwimSubscriptionCountPort> subscriptionRepositories) {
        this.leaderElection = leaderElection;
        this.stateManager = stateManager;
        this.filterCache = filterCache;
        this.idempotencyPort = idempotencyPort;
        this.consumerManager = consumerManager;
        this.filterCacheRefreshIntervalCycles = filterCacheRefreshIntervalCycles;
        this.deleteAndRecreate = deleteAndRecreate;
        this.configParsers = configParsers;
        this.subscriptionServices = subscriptionServices;
        this.subscriptionRepositories = subscriptionRepositories;
    }

    @Scheduled(every = "${reconciliation.retry.interval:10s}", delayed = "${reconciliation.retry.initial-delay:30s}")
    public void retryReconciliation() {
        if (!leaderElection.isLeader()) {
            return;
        }

        if (stateManager.isReconciled()) {
            checkAndReconnect();
            return;
        }

        log.warn("Reconciliation not completed - attempting retry");
        try {
            ReconciliationResult result = performReconciliation();
            if (result.isFullyReconciled()) {
                stateManager.markAsReconciled();
                idempotencyPort.warmup();
                registerActiveConsumers();
            } else {
                log.warn("Reconciliation retry incomplete: {}/{} succeeded, {} failed - will retry next cycle",
                        result.succeeded(), result.desired(), result.failed());
            }
        } catch (Exception e) {
            log.error("Reconciliation retry failed", e);
        }
    }

    private void registerActiveConsumers() {
        if (!stateManager.isReconciled()) {
            log.warn("Skipping consumer registration - reconciliation not completed yet");
            return;
        }
        registerAllActiveConsumers();
    }

    private void checkAndReconnect() {
        reconciliationCycleCount++;
        if (filterCacheRefreshIntervalCycles > 0
                && reconciliationCycleCount % filterCacheRefreshIntervalCycles == 0) {
            filterCache.refreshAll();
        }
        ensureDesiredSubscriptionsExist();
        ensureActiveConsumersConnected();
    }

    private void ensureActiveConsumersConnected() {
        int currentConsumers = consumerManager.getActiveConsumerCount();
        long expectedConsumers = getSubscriptionCount();

        if (currentConsumers < expectedConsumers) {
            log.info("Reconnecting AMQP consumers - Current: {}, Expected: {}", currentConsumers, expectedConsumers);
            consumerManager.resetClient();
            registerAllActiveConsumers();
            return;
        }

        if (consumerManager.hasZombieConsumers()) {
            log.warn("Zombie AMQP consumers detected - forcing reconnection");
            consumerManager.resetClient();
            registerAllActiveConsumers();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected ReconciliationResult performReconciliation() {
        SwimSubscriptionConfigParserPort parser = configParsers.get();
        SwimSubscriptionLifecyclePort service = subscriptionServices.get();

        List<?> desiredSubscriptions = parser.parseDesiredSubscriptions();
        if (desiredSubscriptions.isEmpty()) {
            log.warn("No subscriptions configured - skipping reconciliation");
            return ReconciliationResult.empty();
        }

        service.resetAllSubscriptions(deleteAndRecreate);
        ReconciliationResult result = ((AbstractSubscriptionService) service).reconcileCreate(desiredSubscriptions);
        ((AbstractSubscriptionService) service).reconcileDelete(desiredSubscriptions);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void ensureDesiredSubscriptionsExist() {
        SwimSubscriptionConfigParserPort parser = configParsers.get();
        SwimSubscriptionLifecyclePort service = subscriptionServices.get();

        List<?> desired = parser.parseDesiredSubscriptions();
        if (desired.isEmpty()) {
            return;
        }

        ReconciliationResult result = ((AbstractSubscriptionService) service).reconcileCreate(desired);
        if (result.succeeded() > 0) {
            service.populateFilterCache();
        }
    }

    protected void registerAllActiveConsumers() {
        subscriptionServices.get().registerAllActiveConsumers();
    }

    protected long getSubscriptionCount() {
        return subscriptionRepositories.get().countTotalSubscriptions();
    }
}
