package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
final class SubscriptionActiveConsumerRegistration {

    private SubscriptionActiveConsumerRegistration() {
    }

    static <D, S extends SwimConsumerSubscription> void registerAll(AbstractSubscriptionService<D, S> service) {
        List<S> activeSubscriptions = service.findActiveSubscriptions();
        if (activeSubscriptions.isEmpty()) {
            log.info("No active subscriptions to register");
            return;
        }
        log.info("Registering {} active consumers", activeSubscriptions.size());
        for (S subscription : activeSubscriptions) {
            try {
                ProviderConfiguration provider = service.resolveProvider(subscription.getProviderId());
                service.registerAmqpConsumer(
                        subscription.getSubscriptionId(),
                        subscription.getQueueName(),
                        provider);
                log.info("Consumer registered: {}", subscription.getSubscriptionId());
            } catch (Exception e) {
                log.error("Failed to register consumer: {} — {}", subscription.getSubscriptionId(), ExceptionRootMessage.format(e));
                log.debug("Full stack trace for failed consumer registration: {}", subscription.getSubscriptionId(), e);
            }
        }
        service.onAllConsumersRegistered();
    }
}
