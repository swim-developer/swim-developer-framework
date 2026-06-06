package com.github.swim_developer.framework.consumer.application.heartbeat.schedule;

import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import com.github.swim_developer.framework.domain.model.HeartbeatTimeoutEvent;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.awaitility.Awaitility.await;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(TestNameLoggerExtension.class)
@SuppressWarnings("unchecked")
class SubscriptionHeartbeatCheckerTest {

    private LeaderElection leaderElection;
    private Event<HeartbeatTimeoutEvent> timeoutEvent;
    private SubscriptionHeartbeatTracker tracker;

    @BeforeEach
    void setUp() {
        leaderElection = mock(LeaderElection.class);
        timeoutEvent = mock(Event.class);
        tracker = new SubscriptionHeartbeatTracker();
        when(leaderElection.isLeader()).thenReturn(true);
    }

    @Test
    void check_doesNothing_whenMonitorDisabled() {
        SubscriptionHeartbeatChecker checker = buildChecker(false, Duration.ofSeconds(30));
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", expiredHeartbeat());

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    @Test
    void check_doesNothing_whenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofSeconds(30));
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", expiredHeartbeat());

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    @Test
    void check_doesNotFireTimeout_whenHeartbeatWithinTolerance() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofMinutes(5));
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", freshHeartbeat(Duration.ofMinutes(4)));

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    @Test
    void check_firesTimeout_whenHeartbeatDeadlineExceeded() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofSeconds(5));
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", expiredHeartbeat());

        checker.checkHeartbeatTimeouts();

        ArgumentCaptor<HeartbeatTimeoutEvent> captor = ArgumentCaptor.forClass(HeartbeatTimeoutEvent.class);
        verify(timeoutEvent).fire(captor.capture());
        assertThat(captor.getValue().subscriptionId()).isEqualTo("sub-1");
        assertThat(captor.getValue().lastHeartbeat()).isNotNull();
    }

    @Test
    void check_skipsHeartbeat_withNullNextPublicationTime() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofSeconds(5));
        SubscriptionHeartbeat noNextTime = new SubscriptionHeartbeat(
                UUID.randomUUID(), "ACTIVE", "HEALTHY",
                Instant.now().minusSeconds(60), null, 1L);
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", noNextTime);

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    @Test
    void check_firesTimeout_forSubscriptionWithNoHeartbeatBeyondTolerance() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofMillis(10));
        tracker.registerSubscription("sub-silent");

        Instant deadline = Instant.now().plusMillis(25);
        await().atMost(Duration.ofSeconds(5)).until(() -> Instant.now().isAfter(deadline));

        checker.checkHeartbeatTimeouts();

        ArgumentCaptor<HeartbeatTimeoutEvent> captor = ArgumentCaptor.forClass(HeartbeatTimeoutEvent.class);
        verify(timeoutEvent).fire(captor.capture());
        assertThat(captor.getValue().subscriptionId()).isEqualTo("sub-silent");
        assertThat(captor.getValue().lastHeartbeat()).isNull();
    }

    @Test
    void check_doesNotFireTimeout_forSubscriptionWithNoHeartbeatWithinTolerance() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofMinutes(5));
        tracker.registerSubscription("sub-new");

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    @Test
    void check_resetsRegistration_afterFiringTimeoutForSilentSubscription() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofMillis(10));
        tracker.registerSubscription("sub-silent");

        Instant beforeReset = tracker.getSubscriptionsWithoutHeartbeat().get("sub-silent");
        Instant deadline = Instant.now().plusMillis(25);
        await().atMost(Duration.ofSeconds(5)).until(() -> Instant.now().isAfter(deadline));
        checker.checkHeartbeatTimeouts();
        Instant afterReset = tracker.getSubscriptionsWithoutHeartbeat().get("sub-silent");

        assertThat(afterReset).isAfter(beforeReset);
    }

    @Test
    void check_firesTimeoutForMultipleExpiredSubscriptions() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofSeconds(5));
        tracker.registerSubscription("sub-A");
        tracker.registerSubscription("sub-B");
        tracker.recordHeartbeat("sub-A", expiredHeartbeat());
        tracker.recordHeartbeat("sub-B", expiredHeartbeat());

        checker.checkHeartbeatTimeouts();

        verify(timeoutEvent, times(2)).fire(any(HeartbeatTimeoutEvent.class));
    }

    @Test
    void check_doesNotFireTimeout_whenTrackerIsEmpty() {
        SubscriptionHeartbeatChecker checker = buildChecker(true, Duration.ofSeconds(5));

        checker.checkHeartbeatTimeouts();

        verifyNoInteractions(timeoutEvent);
    }

    private SubscriptionHeartbeatChecker buildChecker(boolean enabled, Duration tolerance) {
        SubscriptionHeartbeatChecker c = new SubscriptionHeartbeatChecker(
                enabled, tolerance, tracker, leaderElection,
                new SimpleMeterRegistry(), timeoutEvent);
        c.init();
        return c;
    }

    private static SubscriptionHeartbeat expiredHeartbeat() {
        Instant longAgo = Instant.now().minusSeconds(120);
        return new SubscriptionHeartbeat(
                UUID.randomUUID(), "ACTIVE", "HEALTHY",
                longAgo, longAgo.plusSeconds(30), 1L);
    }

    private static SubscriptionHeartbeat freshHeartbeat(Duration nextIn) {
        Instant now = Instant.now();
        return new SubscriptionHeartbeat(
                UUID.randomUUID(), "ACTIVE", "HEALTHY",
                now, now.plus(nextIn), 1L);
    }
}
