package com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat;

import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class SubscriptionHeartbeatTracker {

    private final Map<String, SubscriptionHeartbeat> lastHeartbeats = new ConcurrentHashMap<>();
    private final Map<String, Instant> registeredSubscriptions = new ConcurrentHashMap<>();

    public void recordHeartbeat(String subscriptionId, SubscriptionHeartbeat heartbeat) {
        lastHeartbeats.put(subscriptionId, heartbeat);
        log.debug("Heartbeat recorded - SubId: {}, State: {}, Next: {}",
                subscriptionId, heartbeat.subscriptionState(), heartbeat.nextPublicationTime());
    }

    public void registerSubscription(String subscriptionId) {
        registeredSubscriptions.putIfAbsent(subscriptionId, Instant.now());
    }

    public void resetRegistration(String subscriptionId) {
        registeredSubscriptions.put(subscriptionId, Instant.now());
    }

    public SubscriptionHeartbeat get(String subscriptionId) {
        return lastHeartbeats.get(subscriptionId);
    }

    public void remove(String subscriptionId) {
        lastHeartbeats.remove(subscriptionId);
        registeredSubscriptions.remove(subscriptionId);
    }

    public Collection<Map.Entry<String, SubscriptionHeartbeat>> entries() {
        return lastHeartbeats.entrySet();
    }

    public boolean isEmpty() {
        return lastHeartbeats.isEmpty();
    }

    public Map<String, Instant> getSubscriptionsWithoutHeartbeat() {
        Map<String, Instant> result = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : registeredSubscriptions.entrySet()) {
            if (!lastHeartbeats.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
