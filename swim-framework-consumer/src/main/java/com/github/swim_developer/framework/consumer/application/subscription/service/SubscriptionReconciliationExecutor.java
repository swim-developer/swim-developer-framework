package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
final class SubscriptionReconciliationExecutor {

    private SubscriptionReconciliationExecutor() {
    }

    static <D, S extends SwimConsumerSubscription> ReconciliationResult runCreate(
            AbstractSubscriptionService<D, S> service,
            List<D> desiredSubscriptions) {
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        for (D desired : desiredSubscriptions) {
            if (service.existsLocally(desired)) {
                skipped++;
                continue;
            }
            try {
                S subscription = service.callCreateAndPersist(desired);
                ProviderConfiguration provider = service.resolveProvider(subscription.getProviderId());
                service.activateSubscription(subscription.getSubscriptionId(), subscription.getQueueName(), provider);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to create subscription: {} — {}", service.describeDesired(desired), ExceptionRootMessage.format(e));
                log.debug("Full stack trace for failed subscription: {}", service.describeDesired(desired), e);
            }
        }

        ReconciliationResult result = new ReconciliationResult(desiredSubscriptions.size(), succeeded, failed, skipped);
        if (succeeded > 0 || failed > 0) {
            log.info("Reconciliation create result: {}/{} succeeded, {} failed, {} skipped",
                    succeeded, desiredSubscriptions.size(), failed, skipped);
        }
        return result;
    }

    static <D, S extends SwimConsumerSubscription> void runDelete(AbstractSubscriptionService<D, S> service, List<D> desiredSubscriptions) {
        List<S> currentSubscriptions = service.loadDeclaredSubscriptions();
        for (S current : currentSubscriptions) {
            if (!service.isStillDesired(current, desiredSubscriptions)) {
                try {
                    service.deactivateAndDelete(current);
                } catch (Exception e) {
                    log.error("Failed to delete subscription: {} — {}", current.getSubscriptionId(), ExceptionRootMessage.format(e));
                    log.debug("Full stack trace for failed subscription deletion: {}", current.getSubscriptionId(), e);
                }
            }
        }
    }
}
