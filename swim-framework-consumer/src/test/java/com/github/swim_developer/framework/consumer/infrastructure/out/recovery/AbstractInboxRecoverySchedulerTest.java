package com.github.swim_developer.framework.consumer.infrastructure.out.recovery;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractInboxRecoverySchedulerTest {

    record StubInbox(String id, int retries, Instant lastAttempt, boolean maxRetriesExceeded) {}

    private LeaderElection leaderElection;
    private List<StubInbox> dlqReceived;
    private List<StubInbox> reprocessed;
    private AbstractInboxRecoveryScheduler<StubInbox> scheduler;

    @BeforeEach
    void setUp() {
        leaderElection = mock(LeaderElection.class);
        dlqReceived = new ArrayList<>();
        reprocessed = new ArrayList<>();
    }

    private AbstractInboxRecoveryScheduler<StubInbox> schedulerWith(List<StubInbox> stuckMessages) {
        return new AbstractInboxRecoveryScheduler<>(leaderElection, 1, 100) {
            @Override
            protected List<StubInbox> findStuckInbox(int batchSize) { return stuckMessages; }

            @Override
            protected boolean exceedsMaxRetries(StubInbox inbox) { return inbox.maxRetriesExceeded(); }

            @Override
            protected void handleMaxRetriesExceeded(StubInbox inbox) { dlqReceived.add(inbox); }

            @Override
            protected void incrementRetryCount(StubInbox inbox) { /* not exercised in this test */ }

            @Override
            protected void reprocess(StubInbox inbox) { reprocessed.add(inbox); }

            @Override
            protected Instant getLastAttemptTime(StubInbox inbox) { return inbox.lastAttempt(); }

            @Override
            protected int getRetryCount(StubInbox inbox) { return inbox.retries(); }
        };
    }

    @Test
    void recoverStuckMessages_skipsWhenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);
        scheduler = schedulerWith(List.of(new StubInbox("i1", 0, null, false)));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).isEmpty();
    }

    @Test
    void recoverStuckMessages_skipsWhenNoStuckMessages() {
        when(leaderElection.isLeader()).thenReturn(true);
        scheduler = schedulerWith(List.of());

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).isEmpty();
        assertThat(dlqReceived).isEmpty();
    }

    @Test
    void recoverStuckMessages_sendsToDlqWhenMaxRetriesExceeded() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 5, null, true);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(dlqReceived).containsExactly(inbox);
        assertThat(reprocessed).isEmpty();
    }

    @Test
    void recoverStuckMessages_reprocessesWhenNoLastAttempt() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 0, null, false);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).containsExactly(inbox);
        assertThat(dlqReceived).isEmpty();
    }

    @Test
    void recoverStuckMessages_reprocessesWhenZeroRetries() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 0, Instant.now().minusSeconds(60), false);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).containsExactly(inbox);
    }

    @Test
    void recoverStuckMessages_defersWhenBackoffNotElapsed() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 1, Instant.now(), false);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).isEmpty();
        assertThat(dlqReceived).isEmpty();
    }

    @Test
    void recoverStuckMessages_reprocessesWhenBackoffElapsed() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 1, Instant.now().minusSeconds(120), false);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).containsExactly(inbox);
    }

    @Test
    void calculateBackoffMinutes_doublesWithRetryCount() {
        assertThat(AbstractInboxRecoveryScheduler.calculateBackoffMinutes(2, 1)).isEqualTo(2);
        assertThat(AbstractInboxRecoveryScheduler.calculateBackoffMinutes(2, 2)).isEqualTo(4);
        assertThat(AbstractInboxRecoveryScheduler.calculateBackoffMinutes(2, 3)).isEqualTo(8);
    }

    @Test
    void calculateBackoffMinutes_capsAtMaxShift() {
        long capped = AbstractInboxRecoveryScheduler.calculateBackoffMinutes(2, 10);
        long atMax = AbstractInboxRecoveryScheduler.calculateBackoffMinutes(2, 5);
        assertThat(capped).isEqualTo(atMax);
    }

    @Test
    void isLeader_delegatesToLeaderElection() {
        when(leaderElection.isLeader()).thenReturn(true);
        scheduler = schedulerWith(List.of());

        scheduler.recoverStuckMessages();

        verify(leaderElection).isLeader();
    }

    @Test
    void isBackoffElapsed_returnsTrueWhenNoLastAttempt() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubInbox inbox = new StubInbox("i1", 2, null, false);
        scheduler = schedulerWith(List.of(inbox));

        scheduler.recoverStuckMessages();

        assertThat(reprocessed).containsExactly(inbox);
    }
}
