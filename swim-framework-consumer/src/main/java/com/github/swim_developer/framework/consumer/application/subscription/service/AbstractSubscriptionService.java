package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.application.port.out.SwimConsumerManagerPort;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.util.List;
import java.util.Optional;

@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractSubscriptionService<D, S extends SwimConsumerSubscription>
        implements SwimSubscriptionLifecyclePort {

    private final SwimConsumerManagerPort consumerManager;

    protected AbstractSubscriptionService() {
        this.consumerManager = null;
    }

    protected AbstractSubscriptionService(SwimConsumerManagerPort consumerManager) {
        this.consumerManager = consumerManager;
    }

    public ReconciliationResult reconcileCreate(List<D> desiredSubscriptions) {
        return SubscriptionReconciliationExecutor.runCreate(this, desiredSubscriptions);
    }

    public void reconcileDelete(List<D> desiredSubscriptions) {
        SubscriptionReconciliationExecutor.runDelete(this, desiredSubscriptions);
    }

    @Retry(maxRetries = 3, delay = 2000, jitter = 1000, abortOn = SubscriptionNotFoundException.class)
    @Fallback(fallbackMethod = "onActivationFailed")
    public void activateSubscription(String subscriptionId, String queueName, ProviderConfiguration provider) {
        SubscriptionActivationExecutor.run(this, subscriptionId, queueName, provider);
    }

    protected void registerAmqpConsumer(String subscriptionId, String queueName, ProviderConfiguration provider) {
        consumerManager.registerConsumer(subscriptionId, queueName, provider);
    }

    protected void pauseAmqpConsumer(String subscriptionId) {
        consumerManager.pauseConsumer(subscriptionId);
    }

    protected void unregisterAmqpConsumer(String subscriptionId) {
        consumerManager.unregisterConsumer(subscriptionId);
    }

    protected String activateRemoteSubscription(String subscriptionId) {
        String confirmedStatus = callUpdateStatus(subscriptionId, "ACTIVE");
        return confirmedStatus != null ? confirmedStatus : "ACTIVE";
    }

    @SuppressWarnings("unused")
    protected void onActivationFailed(String subscriptionId, String queueName, ProviderConfiguration provider) {
        Optional<S> existing = findBySubscriptionId(subscriptionId);
        if (existing.isEmpty()) {
            log.info("Subscription {} already cleaned up by recovery flow", subscriptionId);
            return;
        }

        log.error("MANUAL ACTION REQUIRED: Failed to activate subscription {} after retries. Queue: {}",
                  subscriptionId, queueName);
        try {
            updateLocalStatus(subscriptionId, "FAILED_ACTIVATION");
        } catch (Exception e) {
            log.error("Failed to update status to FAILED_ACTIVATION: {} — {}", subscriptionId, ExceptionRootMessage.format(e));
        }
    }

    protected void deactivateAndDelete(S subscription) {
        String subscriptionId = subscription.getSubscriptionId();
        unregisterAmqpConsumer(subscriptionId);
        callDeleteRemoteSubscription(subscriptionId);
        deleteLocalSubscription(subscriptionId);
    }

    public void deleteSubscriptionById(String subscriptionId) {
        SubscriptionLifecycleExecutor.deleteById(this, subscriptionId);
    }

    public S pauseSubscription(String subscriptionId) {
        return SubscriptionLifecycleExecutor.pause(this, subscriptionId);
    }

    public S resumeSubscription(String subscriptionId) {
        return SubscriptionLifecycleExecutor.resume(this, subscriptionId);
    }

    protected abstract S callCreateAndPersist(D desired);

    protected abstract String callUpdateStatus(String subscriptionId, String newStatus);

    protected abstract void callDeleteRemoteSubscription(String subscriptionId);

    protected abstract boolean existsLocally(D desired);

    public abstract Optional<S> findBySubscriptionId(String subscriptionId);

    protected abstract List<S> loadDeclaredSubscriptions();

    protected abstract boolean isStillDesired(S current, List<D> desiredSubscriptions);

    protected abstract void deleteLocalSubscription(String subscriptionId);

    protected abstract void updateLocalStatus(String subscriptionId, String status);

    protected abstract String describeDesired(D desired);

    public abstract ProviderConfiguration resolveProvider(String providerId);

    public void registerAllActiveConsumers() {
        SubscriptionActiveConsumerRegistration.registerAll(this);
    }

    protected abstract List<S> findActiveSubscriptions();

    protected void onAllConsumersRegistered() {
    }

    public void resetAllSubscriptions(boolean deleteAndRecreate) {
    }

    public void populateFilterCache() {
    }

    public long countSubscriptions() {
        return findActiveSubscriptions().size();
    }

    public void handleSubscriptionLost(String subscriptionId, S subscription) {
        SubscriptionLossRecoveryExecutor.run(this, subscriptionId, subscription);
    }

    @SuppressWarnings("unused")
    protected Optional<D> toDesiredConfig(S subscription) {
        return Optional.empty();
    }

    protected void syncFromProvider(D desiredConfig) {
        log.warn("syncFromProvider not implemented. Conflict for {} will resolve on next reconciliation.",
                describeDesired(desiredConfig));
    }
}
