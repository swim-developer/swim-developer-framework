package com.github.swim_developer.framework.consumer.application.heartbeat.timeout;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.out.client.SwimSubscriptionManagerClientPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import com.github.swim_developer.framework.domain.model.HeartbeatTimeoutEvent;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class AbstractHeartbeatTimeoutHandler {

    private static final Set<String> TERMINAL_STATES = Set.of("TERMINATED", "DELETED", "INVALID");

    private final SubscriptionHeartbeatTracker heartbeatTracker;
    private final Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private final Instance<SwimSubscriptionManagerClientPort> smRegistries;

    protected AbstractHeartbeatTimeoutHandler() {
        this.heartbeatTracker = null;
        this.subscriptionServices = null;
        this.smRegistries = null;
    }

    @Inject
    public AbstractHeartbeatTimeoutHandler(
            SubscriptionHeartbeatTracker heartbeatTracker,
            Instance<SwimSubscriptionLifecyclePort> subscriptionServices,
            Instance<SwimSubscriptionManagerClientPort> smRegistries) {
        this.heartbeatTracker = heartbeatTracker;
        this.subscriptionServices = subscriptionServices;
        this.smRegistries = smRegistries;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onHeartbeatTimeout(@Observes HeartbeatTimeoutEvent event) {
        String subscriptionId = event.subscriptionId();
        log.debug("Handling heartbeat timeout for subscription: {}", subscriptionId);

        AbstractSubscriptionService service = (AbstractSubscriptionService) subscriptionServices.get();
        Optional<?> local = service.findBySubscriptionId(subscriptionId);
        if (local.isEmpty()) {
            log.info("Subscription {} already deleted locally, removing from tracker", subscriptionId);
            heartbeatTracker.remove(subscriptionId);
            return;
        }

        SwimConsumerSubscription subscription = (SwimConsumerSubscription) local.get();

        try {
            ProviderConfiguration provider = service.resolveProvider(subscription.getProviderId());
            SwimSubscriptionManagerClientPort registry = smRegistries.get();
            String remoteStatus = registry.querySubscriptionStatus(subscriptionId, provider);

            if (TERMINAL_STATES.contains(remoteStatus)) {
                log.warn("Provider says subscription {} is {}, triggering recovery", subscriptionId, remoteStatus);
                service.handleSubscriptionLost(subscriptionId, subscription);
            } else {
                log.debug("Provider says subscription {} is {} but heartbeats absent - possible broker issue",
                        subscriptionId, remoteStatus);
            }
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 404 || status == 410) {
                log.warn("Provider returned {} for subscription {}, triggering recovery", status, subscriptionId);
                service.handleSubscriptionLost(subscriptionId, subscription);
            } else {
                log.error("Unexpected error checking subscription {} on provider: HTTP {}",
                        subscriptionId, status, e);
            }
        } catch (Exception e) {
            log.error("Failed to check subscription {} on provider, will retry next cycle", subscriptionId, e);
        }
    }
}
