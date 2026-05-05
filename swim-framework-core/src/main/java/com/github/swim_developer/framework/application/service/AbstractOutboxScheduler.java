package com.github.swim_developer.framework.application.service;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class AbstractOutboxScheduler<E> {

    private final LeaderElection leaderElection;
    private final int maxRetries;
    private final int recoveryBatchSize;

    protected AbstractOutboxScheduler() {
        this.leaderElection = null;
        this.maxRetries = 0;
        this.recoveryBatchSize = 0;
    }

    protected AbstractOutboxScheduler(LeaderElection leaderElection, int maxRetries, int recoveryBatchSize) {
        this.leaderElection = leaderElection;
        this.maxRetries = maxRetries;
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public void recoverOrphanedEvents() {
        if (!leaderElection.isLeader()) {
            return;
        }

        List<E> pendingEvents = findPendingEvents(recoveryBatchSize);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.warn("Outbox recovery: {} pending events found", pendingEvents.size());

        for (E event : pendingEvents) {
            if (exceedsMaxRetries(event)) {
                log.error("Event exceeded max retries, requires manual intervention");
                handleMaxRetriesExceeded(event);
                continue;
            }
            redispatch(event);
        }
    }

    protected int getMaxRetries() {
        return maxRetries;
    }

    protected int getBatchSize() {
        return recoveryBatchSize;
    }

    protected abstract List<E> findPendingEvents(int batchSize);

    protected abstract boolean exceedsMaxRetries(E event);

    protected abstract void handleMaxRetriesExceeded(E event);

    protected abstract void redispatch(E event);
}
