package com.github.swim_developer.framework.consumer.infrastructure.out.recovery;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled recovery for SWIM service inbox messages that become stuck in
 * intermediate states (RECEIVED, PROCESSING) due to transient failures.
 *
 * <h2>Intended use case</h2>
 * <p>SWIM services that use an inbox table/collection as a durable landing zone
 * for inbound AMQP messages (before handing off to processing) need a watchdog
 * that detects and retries stuck entries. Typical scenarios:</p>
 * <ul>
 *   <li><strong>FF-ICE consumer</strong>: eFPL messages received from NM B2B
 *       that failed mid-processing (e.g., database timeout during persistence).</li>
 *   <li>Any consumer where the AMQP message is ACKed on receipt but
 *       processing happens asynchronously — a crash between ACK and commit
 *       would leave the inbox entry in a non-terminal state.</li>
 * </ul>
 *
 * <h2>Recovery algorithm</h2>
 * <ol>
 *   <li>Only the cluster leader runs recovery (via {@link com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection}).</li>
 *   <li>Queries entries that have been in RECEIVED/PROCESSING state longer
 *       than the configured timeout.</li>
 *   <li>Applies exponential backoff before each retry attempt.</li>
 *   <li>After {@code swim.inbox.recovery.max-retries} attempts, sends to DLQ.</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>
 * swim.inbox.recovery.received.timeout-minutes=2
 * swim.inbox.recovery.processing.timeout-minutes=2
 * swim.inbox.recovery.max-retries=3
 * swim.inbox.recovery.batch-size=100
 * </pre>
 *
 * @param <I> the inbox entity type (must be a persistent entity with status,
 *            retry count, and timestamp fields)
 */
@Slf4j
public abstract class AbstractInboxRecoveryScheduler<I> {

    private final LeaderElection leaderElection;
    private final int processingTimeoutMinutes;
    private final int recoveryBatchSize;

    @Inject
    protected AbstractInboxRecoveryScheduler(
            LeaderElection leaderElection,
            @ConfigProperty(name = "swim.inbox.recovery.processing.timeout-minutes", defaultValue = "2") int processingTimeoutMinutes,
            @ConfigProperty(name = "swim.inbox.recovery.batch-size", defaultValue = "100") int recoveryBatchSize) {
        this.leaderElection = leaderElection;
        this.processingTimeoutMinutes = processingTimeoutMinutes;
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public void recoverStuckMessages() {
        if (!isLeader()) {
            return;
        }

        List<I> stuckMessages = findStuckInbox(getBatchSize());
        if (stuckMessages.isEmpty()) {
            return;
        }

        log.info("Inbox recovery: {} stuck messages found", stuckMessages.size());

        int reprocessed = 0;
        int deferred = 0;
        int dlq = 0;

        for (I inbox : stuckMessages) {
            if (exceedsMaxRetries(inbox)) {
                log.error("Inbox exceeded max retries, sending to DLQ");
                handleMaxRetriesExceeded(inbox);
                dlq++;
            } else if (!isBackoffElapsed(inbox)) {
                deferred++;
            } else {
                incrementRetryCount(inbox);
                reprocess(inbox);
                reprocessed++;
            }
        }

        if (deferred > 0) {
            log.info("Inbox recovery: {} reprocessed, {} deferred (backoff), {} to DLQ",
                    reprocessed, deferred, dlq);
        }
    }

    protected boolean isBackoffElapsed(I inbox) {
        Instant lastAttempt = getLastAttemptTime(inbox);
        if (lastAttempt == null) {
            return true;
        }
        int retries = getRetryCount(inbox);
        if (retries <= 0) {
            return true;
        }
        long backoffMinutes = processingTimeoutMinutes * (1L << Math.min(retries - 1, 4));
        return Instant.now().isAfter(lastAttempt.plusSeconds(backoffMinutes * 60));
    }

    static long calculateBackoffMinutes(int baseMinutes, int retryCount) {
        return baseMinutes * (1L << Math.min(retryCount - 1, 4));
    }

    protected boolean isLeader() {
        return leaderElection.isLeader();
    }

    protected abstract List<I> findStuckInbox(int batchSize);

    protected int getBatchSize() {
        return recoveryBatchSize;
    }

    protected abstract boolean exceedsMaxRetries(I inbox);

    protected abstract void handleMaxRetriesExceeded(I inbox);

    protected abstract void incrementRetryCount(I inbox);

    protected abstract void reprocess(I inbox);

    protected abstract Instant getLastAttemptTime(I inbox);

    protected abstract int getRetryCount(I inbox);
}
