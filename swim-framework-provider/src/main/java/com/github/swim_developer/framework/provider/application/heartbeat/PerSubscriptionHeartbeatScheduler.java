package com.github.swim_developer.framework.provider.application.heartbeat;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.domain.model.ActiveSubscriptionInfo;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.application.port.out.ActiveSubscriptionSupplier;
import com.github.swim_developer.framework.application.port.out.SubscriptionHeartbeatPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ApplicationScoped
public class PerSubscriptionHeartbeatScheduler {

    private final boolean heartbeatEnabled;
    private final String intervalStr;
    private final String providerId;
    private final Instance<SubscriptionHeartbeatPublisher> publisherInstance;
    private final Instance<ActiveSubscriptionSupplier> supplierInstance;
    private final MeterRegistry meterRegistry;
    private final LeaderElection leaderElection;

    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private Counter heartbeatsSentCounter;
    private Counter heartbeatsFailedCounter;

    @Inject
    public PerSubscriptionHeartbeatScheduler(
            @ConfigProperty(name = "swim.heartbeat.enabled", defaultValue = "true") boolean heartbeatEnabled,
            @ConfigProperty(name = "swim.heartbeat.interval", defaultValue = "15s") String intervalStr,
            @ConfigProperty(name = "swim.heartbeat.provider-id", defaultValue = "${HOSTNAME:swim-provider}") String providerId,
            Instance<SubscriptionHeartbeatPublisher> publisherInstance,
            Instance<ActiveSubscriptionSupplier> supplierInstance,
            MeterRegistry meterRegistry,
            LeaderElection leaderElection) {
        this.heartbeatEnabled = heartbeatEnabled;
        this.intervalStr = intervalStr;
        this.providerId = providerId;
        this.publisherInstance = publisherInstance;
        this.supplierInstance = supplierInstance;
        this.meterRegistry = meterRegistry;
        this.leaderElection = leaderElection;
    }

    @PostConstruct
    void initMetrics() {
        heartbeatsSentCounter = Counter.builder("swim_per_sub_heartbeats_sent_total")
                .description("Total per-subscription heartbeat messages sent")
                .tag("provider_id", providerId)
                .register(meterRegistry);

        heartbeatsFailedCounter = Counter.builder("swim_per_sub_heartbeats_failed_total")
                .description("Total per-subscription heartbeat send failures")
                .tag("provider_id", providerId)
                .register(meterRegistry);

        if (!publisherInstance.isResolvable()) {
            log.warn("No SubscriptionHeartbeatPublisher found - per-subscription heartbeat disabled");
        }
        if (!supplierInstance.isResolvable()) {
            log.warn("No ActiveSubscriptionSupplier found - per-subscription heartbeat disabled");
        }
    }

    @Scheduled(every = "${swim.heartbeat.interval:15s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void sendHeartbeats() {
        if (!heartbeatEnabled) {
            return;
        }
        if (!publisherInstance.isResolvable() || !supplierInstance.isResolvable()) {
            return;
        }
        if (!leaderElection.isLeader()) {
            return;
        }

        List<ActiveSubscriptionInfo> subscriptions = supplierInstance.get().getActiveSubscriptions();
        if (subscriptions.isEmpty()) {
            log.trace("No active subscriptions, skipping heartbeat cycle");
            return;
        }

        long seq = sequenceCounter.incrementAndGet();
        Instant now = Instant.now();
        Duration interval = parseInterval(intervalStr);
        Instant next = now.plus(interval);
        SubscriptionHeartbeatPublisher publisher = publisherInstance.get();

        for (ActiveSubscriptionInfo sub : subscriptions) {
            SubscriptionHeartbeat payload = new SubscriptionHeartbeat(
                    sub.subscriptionId(), sub.status(), "OPERATIONAL", now, next, seq
            );

            try {
                publisher.publishHeartbeat(sub.queueName(), payload);
                heartbeatsSentCounter.increment();
                log.trace("Per-sub heartbeat sent - Queue: {}, SubId: {}, Seq: {}", sub.queueName(), sub.subscriptionId(), seq);
            } catch (Exception e) {
                heartbeatsFailedCounter.increment();
                log.error("Failed to send per-sub heartbeat - Queue: {}, SubId: {}", sub.queueName(), sub.subscriptionId(), e);
            }
        }

        log.debug("Per-subscription heartbeat cycle complete: {} subscriptions, seq {}", subscriptions.size(), seq);
    }

    private Duration parseInterval(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(15);
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        }
        if (trimmed.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        }
        return Duration.ofSeconds(15);
    }
}
