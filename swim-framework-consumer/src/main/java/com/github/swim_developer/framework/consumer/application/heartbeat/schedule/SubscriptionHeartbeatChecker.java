package com.github.swim_developer.framework.consumer.application.heartbeat.schedule;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.domain.model.HeartbeatTimeoutEvent;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;


@Slf4j
@ApplicationScoped
public class SubscriptionHeartbeatChecker {

    private final boolean monitorEnabled;
    private final Duration tolerance;
    private final SubscriptionHeartbeatTracker tracker;
    private final LeaderElection leaderElection;
    private final MeterRegistry meterRegistry;
    private final Event<HeartbeatTimeoutEvent> timeoutEvent;

    private Counter timeoutsDetectedCounter;

    @Inject
    public SubscriptionHeartbeatChecker(
            @ConfigProperty(name = "swim.heartbeat.monitor.enabled", defaultValue = "true") boolean monitorEnabled,
            @ConfigProperty(name = "swim.heartbeat.monitor.tolerance", defaultValue = "PT30S") Duration tolerance,
            SubscriptionHeartbeatTracker tracker,
            LeaderElection leaderElection,
            MeterRegistry meterRegistry,
            Event<HeartbeatTimeoutEvent> timeoutEvent) {
        this.monitorEnabled = monitorEnabled;
        this.tolerance = tolerance;
        this.tracker = tracker;
        this.leaderElection = leaderElection;
        this.meterRegistry = meterRegistry;
        this.timeoutEvent = timeoutEvent;
    }

    @PostConstruct
    void init() {
        timeoutsDetectedCounter = Counter.builder("swim_heartbeat_timeouts_detected_total")
                .description("Total per-subscription heartbeat timeouts detected")
                .register(meterRegistry);
        log.info("SubscriptionHeartbeatChecker initialized - tolerance: {}", tolerance);
    }

    @Scheduled(every = "${swim.heartbeat.monitor.check-interval:15s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkHeartbeatTimeouts() {
        if (!monitorEnabled) {
            return;
        }
        if (!leaderElection.isLeader()) {
            return;
        }

        Instant now = Instant.now();

        for (Map.Entry<String, SubscriptionHeartbeat> entry : tracker.entries()) {
            String subscriptionId = entry.getKey();
            SubscriptionHeartbeat lastHb = entry.getValue();

            if (lastHb.nextPublicationTime() == null) {
                continue;
            }

            Instant deadline = lastHb.nextPublicationTime().plus(tolerance);
            if (now.isAfter(deadline)) {
                Duration silence = Duration.between(lastHb.publicationTime(), now);
                log.debug("Heartbeat timeout - SubId: {}, Last: {}, Silence: {}s",
                        subscriptionId, lastHb.publicationTime(), silence.toSeconds());
                timeoutsDetectedCounter.increment();
                timeoutEvent.fire(new HeartbeatTimeoutEvent(subscriptionId, lastHb));
            }
        }

        checkSubscriptionsWithoutHeartbeat(now);
    }

    private void checkSubscriptionsWithoutHeartbeat(Instant now) {
        for (Map.Entry<String, Instant> entry : tracker.getSubscriptionsWithoutHeartbeat().entrySet()) {
            String subscriptionId = entry.getKey();
            Instant registeredAt = entry.getValue();

            if (Duration.between(registeredAt, now).compareTo(tolerance) <= 0) {
                continue;
            }

            log.debug("No heartbeat received since registration - SubId: {}, Registered: {}, Silence: {}s",
                    subscriptionId, registeredAt, Duration.between(registeredAt, now).toSeconds());
            timeoutsDetectedCounter.increment();
            tracker.resetRegistration(subscriptionId);
            timeoutEvent.fire(new HeartbeatTimeoutEvent(subscriptionId, null));
        }
    }

}
