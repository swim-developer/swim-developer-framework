package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.domain.exception.SubscriptionConflictException;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
final class SubscriptionLossRecoveryExecutor {

    private SubscriptionLossRecoveryExecutor() {
    }

    static <D, S extends SwimConsumerSubscription> void run(AbstractSubscriptionService<D, S> service, String subscriptionId, S subscription) {
        log.warn("Provider subscription lost (404/410): {}. Initiating recovery.", subscriptionId);
        service.updateLocalStatus(subscriptionId, "INVALID");
        service.deleteLocalSubscription(subscriptionId);

        Optional<D> desiredConfig = service.toDesiredConfig(subscription);
        if (desiredConfig.isEmpty()) {
            log.warn("Cannot derive desired config for {}. Waiting for next reconciliation cycle.", subscriptionId);
            return;
        }

        try {
            service.reconcileCreate(List.of(desiredConfig.get()));
            log.info("Re-subscription initiated for lost subscription: {}", subscriptionId);
        } catch (SubscriptionConflictException e) {
            log.info("Provider returned 409 during re-subscription of {}. Syncing from provider.", subscriptionId);
            service.syncFromProvider(desiredConfig.get());
        } catch (Exception e) {
            log.error("Re-subscription failed for {}. Will retry on next reconciliation cycle. Cause: {}",
                    subscriptionId, ExceptionRootMessage.format(e));
            log.debug("Full stack trace for re-subscription failure: {}", subscriptionId, e);
        }
    }
}
