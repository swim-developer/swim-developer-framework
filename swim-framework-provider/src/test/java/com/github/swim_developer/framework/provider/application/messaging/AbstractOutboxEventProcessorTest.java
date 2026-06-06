package com.github.swim_developer.framework.provider.application.messaging;

import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractOutboxEventProcessorTest {

    private HandoffCache cache;
    private SimpleMeterRegistry registry;
    private StubProcessor processor;

    @BeforeEach
    void setUp() {
        cache = mock(HandoffCache.class);
        registry = new SimpleMeterRegistry();
        processor = new StubProcessor(cache, registry);
    }

    // ── processOutboxEvent ─────────────────────────────────────────────────────

    @Test
    void processOutboxEvent_logsAndReturns_whenEntityNotFound() {
        when(cache.getAndRemove(anyString(), any())).thenReturn(Optional.empty());
        processor.stubEntity = null;

        processor.processOutboxEvent("evt-missing");

        verify(cache).getAndRemove("evt-missing", TestEvent.class);
    }

    @Test
    void processOutboxEvent_deliversAndUpdatesState_onSuccess() {
        TestEvent entity = event("evt-1", EventStatus.RECEIVED, 0);
        processor.stubEntity = entity;
        processor.deliveryResult = new DeliveryResult(1, 1, 0);
        when(cache.getAndRemove("evt-1", TestEvent.class)).thenReturn(Optional.empty());

        processor.processOutboxEvent("evt-1");

        verify(entity).setStatus(EventStatus.DELIVERED);
    }

    @Test
    void processOutboxEvent_setsPartialDelivery_whenSomeSucceedSomeFail() {
        TestEvent entity = event("evt-2", EventStatus.RECEIVED, 0);
        processor.stubEntity = entity;
        processor.deliveryResult = new DeliveryResult(2, 1, 1);
        when(cache.getAndRemove("evt-2", TestEvent.class)).thenReturn(Optional.empty());

        processor.processOutboxEvent("evt-2");

        verify(entity).setStatus(EventStatus.PARTIALLY_DELIVERED);
    }

    @Test
    void processOutboxEvent_incrementsRetryCount_whenAllFail() {
        TestEvent entity = event("evt-3", EventStatus.RECEIVED, 2);
        processor.stubEntity = entity;
        processor.deliveryResult = new DeliveryResult(1, 0, 1);
        when(cache.getAndRemove("evt-3", TestEvent.class)).thenReturn(Optional.empty());

        processor.processOutboxEvent("evt-3");

        verify(entity).setRetryCount(3);
    }

    @Test
    void processOutboxEvent_incrementsRetryCount_onException() {
        TestEvent entity = event("evt-err", EventStatus.RECEIVED, 1);
        processor.stubEntity = entity;
        processor.deliverException = new RuntimeException("AMQP down");
        when(cache.getAndRemove("evt-err", TestEvent.class)).thenReturn(Optional.empty());

        processor.processOutboxEvent("evt-err");

        verify(entity).setRetryCount(2);
    }

    @Test
    void loadEntity_returnsFromCache_whenPresent() {
        TestEvent cached = event("evt-c", EventStatus.RECEIVED, 0);
        processor.stubMerge = cached;
        when(cache.getAndRemove("evt-c", TestEvent.class)).thenReturn(Optional.of(cached));

        TestEvent result = processor.loadEntityPublic("evt-c");

        assertThat(result).isEqualTo(cached);
    }

    @Test
    void loadEntity_fallsBackToDatabase_onCacheMiss() {
        TestEvent dbEntity = event("evt-db", EventStatus.RECEIVED, 0);
        processor.stubEntity = dbEntity;
        when(cache.getAndRemove("evt-db", TestEvent.class)).thenReturn(Optional.empty());

        TestEvent result = processor.loadEntityPublic("evt-db");

        assertThat(result).isEqualTo(dbEntity);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TestEvent event(String id, EventStatus status, int retryCount) {
        TestEvent e = mock(TestEvent.class);
        when(e.getEventId()).thenReturn(id);
        when(e.getStatus()).thenReturn(status);
        when(e.getRetryCount()).thenReturn(retryCount);
        return e;
    }

    interface TestEvent extends SwimProviderEvent {}

    static class StubProcessor extends AbstractOutboxEventProcessor<TestEvent> {

        TestEvent stubEntity;
        TestEvent stubMerge;
        DeliveryResult deliveryResult = new DeliveryResult(1, 1, 0);
        RuntimeException deliverException;

        StubProcessor(HandoffCache cache, SimpleMeterRegistry registry) {
            super(cache, registry);
        }

        @Override protected TestEvent findEntityById(String id) { return stubEntity; }
        @Override protected TestEvent mergeEntity(TestEvent e) { return stubMerge != null ? stubMerge : e; }
        @Override protected Class<TestEvent> getEntityClass() { return TestEvent.class; }
        @Override protected String getMetricPrefix() { return "test"; }
        @Override protected DeliveryResult deliver(TestEvent entity) {
            if (deliverException != null) throw deliverException;
            return deliveryResult;
        }

        public TestEvent loadEntityPublic(String id) { return loadEntity(id); }
    }
}
