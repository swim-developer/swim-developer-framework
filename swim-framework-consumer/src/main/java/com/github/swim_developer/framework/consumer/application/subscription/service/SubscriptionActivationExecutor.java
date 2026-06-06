package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
final class SubscriptionActivationExecutor {

    private SubscriptionActivationExecutor() {
    }

    static <D, S extends SwimConsumerSubscription> void run(
            AbstractSubscriptionService<D, S> service,
            String subscriptionId,
            String queueName,
            ProviderConfiguration provider) {
        try {
            service.registerAmqpConsumer(subscriptionId, queueName, provider);
            String confirmedStatus = service.activateRemoteSubscription(subscriptionId);
            service.updateLocalStatus(subscriptionId, confirmedStatus);
        } catch (SubscriptionNotFoundException e) {
            log.warn("Provider lost subscription during activation: {}", subscriptionId);
            service.unregisterAmqpConsumer(subscriptionId);
            Optional<S> existing = service.findBySubscriptionId(subscriptionId);
            existing.ifPresent(s -> service.handleSubscriptionLost(subscriptionId, s));
            throw e;
        } catch (Exception e) {
            log.error("Failed to activate: {} — {}", subscriptionId, ExceptionRootMessage.format(e));
            log.debug("Full stack trace for failed activation: {}", subscriptionId, e);
            service.unregisterAmqpConsumer(subscriptionId);
            throw e;
        }
    }
}
