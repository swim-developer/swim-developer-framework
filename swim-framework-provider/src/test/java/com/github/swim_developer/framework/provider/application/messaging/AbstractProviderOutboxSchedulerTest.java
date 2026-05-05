package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractProviderOutboxSchedulerTest {

    private HandoffCache cache;
    private Vertx vertx;
    private EventBus eventBus;
    private LeaderElection leaderElection;
    private SimpleMeterRegistry registry;
    private StubScheduler scheduler;

    @BeforeEach
    void setUp() {
        cache = mock(HandoffCache.class);
        vertx = mock(Vertx.class);
        eventBus = mock(EventBus.class);
        leaderElection = mock(LeaderElection.class);
        registry = new SimpleMeterRegistry();
        when(vertx.eventBus()).thenReturn(eventBus);
        scheduler = new StubScheduler(cache, vertx, registry, leaderElection, 50, 5);
    }

    @Test
    void exceedsMaxRetries_returnsTrue_whenRetryCountReachesMax() {
        TestEvent event = eventWithRetries(5);
        assertThat(scheduler.exceedsMaxRetriesPublic(event)).isTrue();
    }

    @Test
    void exceedsMaxRetries_returnsFalse_whenBelowMax() {
        TestEvent event = eventWithRetries(3);
        assertThat(scheduler.exceedsMaxRetriesPublic(event)).isFalse();
    }

    @Test
    void handleMaxRetriesExceeded_setsDeadLetterStatus() {
        TestEvent event = mock(TestEvent.class);
        when(event.getEventId()).thenReturn("evt-dl");
        when(event.getRetryCount()).thenReturn(5);

        scheduler.handleMaxRetriesExceededPublic(event);

        verify(event).setStatus(EventStatus.DEAD_LETTER);
    }

    @Test
    void redispatch_putsInCacheAndPublishesToBus() {
        TestEvent event = mock(TestEvent.class);
        when(event.getEventId()).thenReturn("evt-rd");
        when(event.getRetryCount()).thenReturn(2);

        scheduler.redispatchPublic(event);

        verify(cache).put("evt-rd", event);
        verify(eventBus).publish("swim.outbox.test", "evt-rd");
    }

    @Test
    void redispatch_logsError_whenPublishFails() {
        TestEvent event = mock(TestEvent.class);
        when(event.getEventId()).thenReturn("evt-fail");
        when(event.getRetryCount()).thenReturn(1);
        doThrow(new RuntimeException("bus error")).when(eventBus).publish(any(), any());

        scheduler.redispatchPublic(event);

        verify(cache).put("evt-fail", event);
    }

    @Test
    void getCleanupBatchSize_defaultsToBatchSize() {
        assertThat(scheduler.getCleanupBatchSizePublic()).isEqualTo(scheduler.getBatchSizePublic());
    }

    @Test
    void isPayloadCompressionEnabled_defaultsFalse() {
        assertThat(scheduler.isPayloadCompressionEnabledPublic()).isFalse();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TestEvent eventWithRetries(int retries) {
        TestEvent e = mock(TestEvent.class);
        when(e.getRetryCount()).thenReturn(retries);
        return e;
    }

    interface TestEvent extends SwimProviderEvent {}

    static class StubScheduler extends AbstractProviderOutboxScheduler<TestEvent> {

        StubScheduler(HandoffCache cache, Vertx vertx, SimpleMeterRegistry reg,
                      LeaderElection le, int batchSize, int maxRetries) {
            super(cache, vertx, reg, le, batchSize, maxRetries);
        }

        @Override protected String getOutboxEventAddress() { return "swim.outbox.test"; }
        @Override protected String getMetricPrefix() { return "test"; }

        @Override protected List<TestEvent> findPendingEvents(int batchSize) { return List.of(); }

        public boolean exceedsMaxRetriesPublic(TestEvent e) { return exceedsMaxRetries(e); }
        public void handleMaxRetriesExceededPublic(TestEvent e) { handleMaxRetriesExceeded(e); }
        public void redispatchPublic(TestEvent e) { redispatch(e); }
        public int getCleanupBatchSizePublic() { return getCleanupBatchSize(); }
        public boolean isPayloadCompressionEnabledPublic() { return isPayloadCompressionEnabled(); }
        public int getBatchSizePublic() { return getBatchSize(); }
    }
}
