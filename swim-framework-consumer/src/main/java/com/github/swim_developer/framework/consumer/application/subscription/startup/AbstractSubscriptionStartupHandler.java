package com.github.swim_developer.framework.consumer.application.subscription.startup;

import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionCountPort;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription.SwimSubscriptionConfigParserPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Slf4j
@ApplicationScoped
public class AbstractSubscriptionStartupHandler {

    private final Instance<SwimSubscriptionConfigParserPort> configParsers;
    private final Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private final Instance<SwimSubscriptionCountPort> subscriptionRepositories;
    private final ReconciliationStateTracker stateManager;
    private final Vertx vertx;
    private final boolean deleteAndRecreate;

    protected AbstractSubscriptionStartupHandler() {
        this.configParsers = null;
        this.subscriptionServices = null;
        this.subscriptionRepositories = null;
        this.stateManager = null;
        this.vertx = null;
        this.deleteAndRecreate = false;
    }

    @Inject
    public AbstractSubscriptionStartupHandler(
            Instance<SwimSubscriptionConfigParserPort> configParsers,
            Instance<SwimSubscriptionLifecyclePort> subscriptionServices,
            Instance<SwimSubscriptionCountPort> subscriptionRepositories,
            ReconciliationStateTracker stateManager,
            Vertx vertx,
            @ConfigProperty(name = "swim.subscriptions.delete-and-recreate", defaultValue = "false") boolean deleteAndRecreate) {
        this.configParsers = configParsers;
        this.subscriptionServices = subscriptionServices;
        this.subscriptionRepositories = subscriptionRepositories;
        this.stateManager = stateManager;
        this.vertx = vertx;
        this.deleteAndRecreate = deleteAndRecreate;
    }

    void onStart(@Observes StartupEvent event) {
        performStartup();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void performStartup() {
        if (!checkDatabaseConnection()) {
            log.error("Database connection failed - reconciliation will be handled by scheduler");
            return;
        }

        log.info("Database connection OK - starting reconciliation in background");
        vertx.executeBlocking(() -> {
            try {
                SwimSubscriptionConfigParserPort parser = configParsers.get();
                SwimSubscriptionLifecyclePort service = subscriptionServices.get();

                List<?> desiredSubscriptions = parser.parseDesiredSubscriptions();
                if (desiredSubscriptions.isEmpty()) {
                    log.warn("No subscriptions configured - reconciliation will not be marked as complete");
                    return null;
                }

                log.info("Attempting reconciliation with {} desired subscriptions", desiredSubscriptions.size());

                service.resetAllSubscriptions(deleteAndRecreate);
                ReconciliationResult result = ((AbstractSubscriptionService) service).reconcileCreate(desiredSubscriptions);
                ((AbstractSubscriptionService) service).reconcileDelete(desiredSubscriptions);

                if (result.isFullyReconciled()) {
                    stateManager.markAsReconciled();
                    log.info("Reconciliation completed successfully - all {} subscriptions created", result.desired());
                    registerActiveConsumers(service);
                } else {
                    log.warn("Reconciliation incomplete: {}/{} succeeded, {} failed - scheduler will retry",
                            result.succeeded(), result.desired(), result.failed());
                }
            } catch (Exception e) {
                log.error("Reconciliation failed - scheduler will retry in background", e);
            }
            return null;
        }, false);
    }

    private void registerActiveConsumers(SwimSubscriptionLifecyclePort service) {
        if (deleteAndRecreate) {
            return;
        }
        if (!stateManager.isReconciled()) {
            log.warn("Skipping consumer registration - reconciliation not completed yet");
            return;
        }
        service.registerAllActiveConsumers();
    }

    protected boolean checkDatabaseConnection() {
        try {
            subscriptionRepositories.get().countTotalSubscriptions();
            return true;
        } catch (Exception e) {
            log.error("MongoDB connection failed", e);
            return false;
        }
    }
}
