package com.github.swim_developer.framework.provider.unit.application;

import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.application.messaging.AbstractFailedDeliveryRecoveryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractFailedDeliveryRecoverySchedulerTest {

    static class StubFD implements SwimFailedDelivery {
        String eventId;
        UUID subscriptionId = UUID.randomUUID();
        String queue;
        int retryCount;
        boolean resolved;
        Instant resolvedAt;
        String errorMessage;

        StubFD(String eventId, String queue, int retryCount) {
            this.eventId = eventId;
            this.queue = queue;
            this.retryCount = retryCount;
        }

        @Override public String getEventId() { return eventId; }
        @Override public UUID getSubscriptionId() { return subscriptionId; }
        @Override public String getQueue() { return queue; }
        @Override public String getErrorMessage() { return errorMessage; }
        @Override public void setErrorMessage(String m) { this.errorMessage = m; }
        @Override public int getRetryCount() { return retryCount; }
        @Override public void setRetryCount(int c) { this.retryCount = c; }
        @Override public boolean isResolved() { return resolved; }
        @Override public void setResolved(boolean r) { this.resolved = r; }
        @Override public void setResolvedAt(Instant t) { this.resolvedAt = t; }
    }

    static class StubEvent implements SwimProviderEvent {
        String id;
        EventStatus status = EventStatus.PARTIALLY_DELIVERED;
        String payload = "<xml/>";

        StubEvent(String id) { this.id = id; }

        @Override public String getEventId() { return id; }
        @Override public String getPayload() { return payload; }
        @Override public EventStatus getStatus() { return status; }
        @Override public void setStatus(EventStatus s) { this.status = s; }
        @Override public int getRetryCount() { return 0; }
        @Override public void setRetryCount(int c) { /* unused */ }
        @Override public int getDeliveredCount() { return 0; }
        @Override public void setDeliveredCount(int c) { /* unused */ }
        @Override public Instant getProcessedAt() { return null; }
        @Override public void setProcessedAt(Instant t) { /* unused */ }
    }

    private LeaderElection leaderElection;
    private List<String> republished;
    private List<StubEvent> events;

    @BeforeEach
    void setUp() {
        leaderElection = mock(LeaderElection.class);
        republished = new ArrayList<>();
        events = new ArrayList<>();
    }

    abstract static class TestableScheduler extends AbstractFailedDeliveryRecoveryScheduler<StubEvent, StubFD> {
        TestableScheduler(LeaderElection le, int maxRetries, int batchSize) {
            super(le, maxRetries, batchSize);
        }
        public void run() { executeRecovery(); }
    }

    @SuppressWarnings("unchecked")
    private TestableScheduler scheduler(List<StubFD> pending, List<StubFD> exceeded) {
        FailedDeliveryStore<StubFD> store = mock(FailedDeliveryStore.class);
        when(store.findPendingRetries(3, 50)).thenReturn(pending);
        when(store.findExceededRetries(3, 50)).thenReturn(exceeded);
        when(store.countPendingByEventId(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);

        return new TestableScheduler(leaderElection, 3, 50) {
            @Override
            protected FailedDeliveryStore<StubFD> getStore() { return store; }

            @Override
            protected StubEvent loadEvent(String eventId) {
                return events.stream().filter(e -> e.id.equals(eventId)).findFirst().orElse(null);
            }

            @Override
            protected void republish(String queue, String payload, QualityOfService qos, UUID subscriptionId) {
                republished.add(queue);
            }
        };
    }

    @Test
    void executeRecovery_skipsWhenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);
        scheduler(List.of(new StubFD("e1", "q1", 0)), List.of()).run();
        assertThat(republished).isEmpty();
    }

    @Test
    void executeRecovery_retriesPendingDeliveries() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubFD fd = new StubFD("e1", "q1", 0);
        events.add(new StubEvent("e1"));
        scheduler(List.of(fd), List.of()).run();
        assertThat(republished).containsExactly("q1");
        assertThat(fd.resolved).isTrue();
    }

    @Test
    void executeRecovery_marksResolvedWhenEventNotFound() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubFD fd = new StubFD("missing", "q1", 0);
        scheduler(List.of(fd), List.of()).run();
        assertThat(republished).isEmpty();
        assertThat(fd.resolved).isTrue();
    }

    @Test
    void executeRecovery_incrementsRetryOnRepublishFailure() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubFD fd = new StubFD("e1", "q1", 1);
        events.add(new StubEvent("e1"));

        @SuppressWarnings("unchecked")
        FailedDeliveryStore<StubFD> store = mock(FailedDeliveryStore.class);
        when(store.findPendingRetries(3, 50)).thenReturn(List.of(fd));
        when(store.findExceededRetries(3, 50)).thenReturn(List.of());
        when(store.countPendingByEventId(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);

        new TestableScheduler(leaderElection, 3, 50) {
            @Override protected FailedDeliveryStore<StubFD> getStore() { return store; }
            @Override protected StubEvent loadEvent(String id) { return events.get(0); }
            @Override protected void republish(String q, String p, QualityOfService qos, UUID sub) {
                throw new RuntimeException("broker down");
            }
        }.run();

        assertThat(fd.retryCount).isEqualTo(2);
        assertThat(fd.resolved).isFalse();
    }

    @Test
    void executeRecovery_marksExceededAsDeadLetter() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubFD fd = new StubFD("e1", "q1", 5);
        scheduler(List.of(), List.of(fd)).run();
        assertThat(fd.resolved).isTrue();
        assertThat(fd.resolvedAt).isNotNull();
    }

    @Test
    void promoteEventIfFullyDelivered_promotesToDelivered() {
        when(leaderElection.isLeader()).thenReturn(true);
        StubEvent event = new StubEvent("e1");
        events.add(event);
        StubFD fd = new StubFD("e1", "q1", 0);
        scheduler(List.of(fd), List.of()).run();
        assertThat(event.status).isEqualTo(EventStatus.DELIVERED);
    }
}
