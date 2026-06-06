package com.github.swim_developer.framework.consumer.application.messaging.outbox;

import com.github.swim_developer.framework.application.model.OutboxDeliveryStatus;
import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(TestNameLoggerExtension.class)
class AbstractOutboxEventConsumerTest {

    private OutboxRouterFanOut fanOut;
    private StubOutboxEventConsumer consumer;

    @BeforeEach
    void setUp() {
        fanOut = mock(OutboxRouterFanOut.class);
        consumer = new StubOutboxEventConsumer(fanOut, 3);
    }

    @Test
    void processOutboxEvent_doesNothing_whenEventNotFound() {
        consumer.processOutboxEvent("unknown-id");

        verify(fanOut, never()).route(any(), any());
    }

    @Test
    void processOutboxEvent_doesNothing_whenEventNotPending() {
        StubEvent event = new StubEvent("msg-1", "<xml/>", OutboxDeliveryStatus.SENT, 0);
        consumer.store("evt-1", event);

        consumer.processOutboxEvent("evt-1");

        verify(fanOut, never()).route(any(), any());
    }

    @Test
    void processOutboxEvent_routesAndMarksSent_onSuccess() {
        StubEvent event = new StubEvent("msg-1", "<xml/>", OutboxDeliveryStatus.PENDING, 0);
        consumer.store("evt-1", event);

        consumer.processOutboxEvent("evt-1");

        verify(fanOut).route("msg-1", "<xml/>");
        assertThat(event.getDeliveryStatus()).isEqualTo(OutboxDeliveryStatus.SENT);
        assertThat(event.getDispatchedAt()).isNotNull();
    }

    @Test
    void processOutboxEvent_incrementsRetry_whenRouteFails() {
        StubEvent event = new StubEvent("msg-2", "<xml/>", OutboxDeliveryStatus.PENDING, 0);
        consumer.store("evt-2", event);
        doThrow(new RuntimeException("outbox unavailable")).when(fanOut).route(any(), any());

        consumer.processOutboxEvent("evt-2");

        assertThat(event.getOutboxRetryCount()).isEqualTo(1);
        assertThat(event.getDeliveryStatus()).isEqualTo(OutboxDeliveryStatus.PENDING);
    }

    @Test
    void processOutboxEvent_marksAsFailed_whenMaxRetriesExceeded() {
        StubEvent event = new StubEvent("msg-3", "<xml/>", OutboxDeliveryStatus.PENDING, 3);
        consumer.store("evt-3", event);
        doThrow(new RuntimeException("outbox unavailable")).when(fanOut).route(any(), any());

        consumer.processOutboxEvent("evt-3");

        assertThat(event.getOutboxRetryCount()).isEqualTo(4);
        assertThat(event.getDeliveryStatus()).isEqualTo(OutboxDeliveryStatus.FAILED);
        verify(fanOut).sendToDeadLetterQueue("msg-3", "<xml/>");
    }

    @Test
    void exceedsMaxRetries_returnsTrue_whenCountEqualsMax() {
        StubEvent event = new StubEvent("msg-4", "<xml/>", OutboxDeliveryStatus.PENDING, 3);
        consumer.store("evt-4", event);

        assertThat(consumer.exceedsMaxRetries(event)).isTrue();
    }

    @Test
    void exceedsMaxRetries_returnsFalse_whenCountBelowMax() {
        StubEvent event = new StubEvent("msg-5", "<xml/>", OutboxDeliveryStatus.PENDING, 2);
        consumer.store("evt-5", event);

        assertThat(consumer.exceedsMaxRetries(event)).isFalse();
    }

    private static class StubOutboxEventConsumer extends AbstractOutboxEventConsumer<StubEvent> {
        private final OutboxRouterFanOut fanOut;
        private final Map<String, StubEvent> store = new HashMap<>();

        StubOutboxEventConsumer(OutboxRouterFanOut fanOut, int maxRetries) {
            super(maxRetries);
            this.fanOut = fanOut;
        }

        void store(String id, StubEvent event) {
            store.put(id, event);
        }

        @Override
        protected StubEvent resolveEvent(String eventId) {
            return store.get(eventId);
        }

        @Override
        protected OutboxRouterFanOut getRouterFanOut() {
            return fanOut;
        }

        @Override
        protected String getEventId(StubEvent event) {
            return event.getMessageId();
        }

        @Override
        protected void updateEvent(StubEvent event) {
            // intentional no-op for test stub
        }
    }

    private static class StubEvent implements SwimOutboxEvent {
        private final String messageId;
        private final String rawPayload;
        private OutboxDeliveryStatus status;
        private int retryCount;
        private Instant dispatchedAt;

        StubEvent(String messageId, String rawPayload, OutboxDeliveryStatus status, int retryCount) {
            this.messageId = messageId;
            this.rawPayload = rawPayload;
            this.status = status;
            this.retryCount = retryCount;
        }

        @Override public String getMessageId() { return messageId; }
        @Override public String getRawPayload() { return rawPayload; }
        @Override public OutboxDeliveryStatus getDeliveryStatus() { return status; }
        @Override public void setDeliveryStatus(OutboxDeliveryStatus s) { this.status = s; }
        @Override public Instant getDispatchedAt() { return dispatchedAt; }
        @Override public void setDispatchedAt(Instant t) { this.dispatchedAt = t; }
        @Override public int getOutboxRetryCount() { return retryCount; }
        @Override public void setOutboxRetryCount(int c) { this.retryCount = c; }
    }
}
