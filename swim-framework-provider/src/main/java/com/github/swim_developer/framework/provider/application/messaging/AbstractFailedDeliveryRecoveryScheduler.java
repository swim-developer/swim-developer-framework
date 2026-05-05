package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Slf4j
public abstract class AbstractFailedDeliveryRecoveryScheduler<
        E extends SwimProviderEvent,
        F extends SwimFailedDelivery> {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final LeaderElection leaderElection;
    private final int maxRetries;
    private final int batchSize;

    protected AbstractFailedDeliveryRecoveryScheduler() {
        this.leaderElection = null;
        this.maxRetries = 5;
        this.batchSize = 50;
    }

    protected AbstractFailedDeliveryRecoveryScheduler(LeaderElection leaderElection, int maxRetries, int batchSize) {
        this.leaderElection = leaderElection;
        this.maxRetries = maxRetries;
        this.batchSize = batchSize;
    }

    protected void executeRecovery() {
        if (leaderElection != null && !leaderElection.isLeader()) {
            return;
        }
        retryFailedDeliveries();
        markExceededAsDeadLetter();
    }

    protected void retryFailedDeliveries() {
        List<F> pending = getStore().findPendingRetries(maxRetries, batchSize);
        if (pending.isEmpty()) {
            return;
        }

        log.info("Recovering {} failed deliveries", pending.size());
        for (F fd : pending) {
            retryDelivery(fd);
        }
    }

    private void retryDelivery(F fd) {
        try {
            E event = loadEvent(fd.getEventId());
            if (event == null) {
                log.warn("Event not found for failed delivery - EventId: {}, marking resolved", fd.getEventId());
                fd.setResolved(true);
                fd.setResolvedAt(Instant.now());
                return;
            }

            republish(fd.getQueue(), event.getPayload(),
                    QualityOfService.AT_LEAST_ONCE, fd.getSubscriptionId());

            fd.setResolved(true);
            fd.setResolvedAt(Instant.now());
            log.info("Failed delivery recovered - EventId: {}, Queue: {}", fd.getEventId(), fd.getQueue());

            onDeliveryRecovered(fd);
            promoteEventIfFullyDelivered(fd.getEventId());

        } catch (Exception e) {
            fd.setRetryCount(fd.getRetryCount() + 1);
            fd.setErrorMessage(truncate(e.getMessage(), MAX_ERROR_LENGTH));
            log.warn("Failed delivery retry failed - EventId: {}, Queue: {}, Attempt: {}",
                    fd.getEventId(), fd.getQueue(), fd.getRetryCount());
        }
    }

    protected void promoteEventIfFullyDelivered(String eventId) {
        long pendingCount = getStore().countPendingByEventId(eventId);
        if (pendingCount == 0) {
            E event = loadEvent(eventId);
            if (event != null && event.getStatus() == EventStatus.PARTIALLY_DELIVERED) {
                event.setStatus(EventStatus.DELIVERED);
                log.info("All failed deliveries resolved, event promoted to DELIVERED - EventId: {}", eventId);
            }
        }
    }

    protected void markExceededAsDeadLetter() {
        List<F> exceeded = getStore().findExceededRetries(maxRetries, batchSize);
        for (F fd : exceeded) {
            fd.setResolved(true);
            fd.setResolvedAt(Instant.now());
            log.error("Failed delivery exceeded max retries - EventId: {}, Queue: {}, Retries: {}",
                    fd.getEventId(), fd.getQueue(), fd.getRetryCount());

            onDeliveryDeadLettered(fd);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    protected abstract FailedDeliveryStore<F> getStore();

    protected abstract E loadEvent(String eventId);

    protected abstract void republish(String queue, String payload,
                                      QualityOfService qos, UUID subscriptionId);


    protected void onDeliveryRecovered(F fd) {
    }

    protected void onDeliveryDeadLettered(F fd) {
    }
}
