package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
final class SubscriptionLifecycleExecutor {

    private SubscriptionLifecycleExecutor() {
    }

    static <D, S extends SwimConsumerSubscription> void deleteById(AbstractSubscriptionService<D, S> service, String subscriptionId) {
        Optional<S> existing = service.findBySubscriptionId(subscriptionId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }

        service.unregisterAmqpConsumer(subscriptionId);
        service.callDeleteRemoteSubscription(subscriptionId);
        service.deleteLocalSubscription(subscriptionId);
        log.info("Deleted subscription: {}", subscriptionId);
    }

    static <D, S extends SwimConsumerSubscription> S pause(AbstractSubscriptionService<D, S> service, String subscriptionId) {
        Optional<S> existing = service.findBySubscriptionId(subscriptionId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }

        try {
            service.pauseAmqpConsumer(subscriptionId);
            String confirmedStatus = service.callUpdateStatus(subscriptionId, "PAUSED");
            service.updateLocalStatus(subscriptionId, confirmedStatus != null ? confirmedStatus : "PAUSED");

            log.info("Paused subscription: {}", subscriptionId);
            return service.findBySubscriptionId(subscriptionId).orElseThrow();
        } catch (SubscriptionNotFoundException e) {
            service.handleSubscriptionLost(subscriptionId, existing.get());
            throw e;
        }
    }

    static <D, S extends SwimConsumerSubscription> S resume(AbstractSubscriptionService<D, S> service, String subscriptionId) {
        Optional<S> existing = service.findBySubscriptionId(subscriptionId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }

        S subscription = existing.get();
        try {
            ProviderConfiguration provider = service.resolveProvider(subscription.getProviderId());
            service.registerAmqpConsumer(subscriptionId, subscription.getQueueName(), provider);
            String confirmedStatus = service.activateRemoteSubscription(subscriptionId);
            service.updateLocalStatus(subscriptionId, confirmedStatus);

            log.info("Resumed subscription: {}", subscriptionId);
            return service.findBySubscriptionId(subscriptionId).orElseThrow();
        } catch (SubscriptionNotFoundException e) {
            service.unregisterAmqpConsumer(subscriptionId);
            service.handleSubscriptionLost(subscriptionId, subscription);
            throw e;
        }
    }
}
