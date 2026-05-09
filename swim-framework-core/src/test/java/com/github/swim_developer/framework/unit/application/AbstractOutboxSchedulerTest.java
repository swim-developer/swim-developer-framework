package com.github.swim_developer.framework.unit.application;

import com.github.swim_developer.framework.application.service.AbstractOutboxScheduler;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractOutboxSchedulerTest {

    record StubEvent(String id, int retryCount, boolean exceedsMax) {}

    private LeaderElection leaderElection;
    private List<StubEvent> redispatched;
    private List<StubEvent> maxRetriesHandled;

    @BeforeEach
    void setUp() {
        leaderElection = mock(LeaderElection.class);
        redispatched = new ArrayList<>();
        maxRetriesHandled = new ArrayList<>();
    }

    private AbstractOutboxScheduler<StubEvent> schedulerWith(List<StubEvent> pending) {
        return new AbstractOutboxScheduler<>(leaderElection, 3, 50) {
            @Override
            protected List<StubEvent> findPendingEvents(int batchSize) { return pending; }

            @Override
            protected boolean exceedsMaxRetries(StubEvent event) { return event.exceedsMax(); }

            @Override
            protected void handleMaxRetriesExceeded(StubEvent event) { maxRetriesHandled.add(event); }

            @Override
            protected void redispatch(StubEvent event) { redispatched.add(event); }
        };
    }

    @Test
    void recoverOrphanedEvents_skipsWhenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);
        var scheduler = schedulerWith(List.of(new StubEvent("e1", 0, false)));

        scheduler.recoverOrphanedEvents();

        assertThat(redispatched).isEmpty();
    }

    @Test
    void recoverOrphanedEvents_skipsWhenNoPendingEvents() {
        when(leaderElection.isLeader()).thenReturn(true);
        var scheduler = schedulerWith(List.of());

        scheduler.recoverOrphanedEvents();

        assertThat(redispatched).isEmpty();
        assertThat(maxRetriesHandled).isEmpty();
    }

    @Test
    void recoverOrphanedEvents_redispatchesNormalEvents() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubEvent event = new StubEvent("e1", 1, false);
        var scheduler = schedulerWith(List.of(event));

        scheduler.recoverOrphanedEvents();

        assertThat(redispatched).containsExactly(event);
        assertThat(maxRetriesHandled).isEmpty();
    }

    @Test
    void recoverOrphanedEvents_handlesMaxRetriesExceeded() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubEvent event = new StubEvent("e1", 5, true);
        var scheduler = schedulerWith(List.of(event));

        scheduler.recoverOrphanedEvents();

        assertThat(maxRetriesHandled).containsExactly(event);
        assertThat(redispatched).isEmpty();
    }

    @Test
    void recoverOrphanedEvents_handlesMultipleEventsWithMixedOutcomes() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubEvent normal = new StubEvent("e1", 1, false);
        StubEvent exceeded = new StubEvent("e2", 5, true);
        var scheduler = schedulerWith(List.of(normal, exceeded));

        scheduler.recoverOrphanedEvents();

        assertThat(redispatched).containsExactly(normal);
        assertThat(maxRetriesHandled).containsExactly(exceeded);
    }

}
