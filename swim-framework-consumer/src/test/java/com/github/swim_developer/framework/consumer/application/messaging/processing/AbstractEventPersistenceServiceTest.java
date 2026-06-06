package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.OutboxDeliveryStatus;
import com.github.swim_developer.framework.application.model.PreparedEvent;
import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxRouterFanOut;
import com.github.swim_developer.framework.domain.exception.ConsumerPersistenceException;
import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractEventPersistenceServiceTest {

    static class StubEvent implements SwimOutboxEvent {
        String messageId;
        String rawPayload;
        OutboxDeliveryStatus status;
        Instant dispatchedAt;

        StubEvent(String messageId, String rawPayload) {
            this.messageId = messageId;
            this.rawPayload = rawPayload;
            this.status = OutboxDeliveryStatus.PENDING;
        }

        @Override public String getMessageId() { return messageId; }
        @Override public String getRawPayload() { return rawPayload; }
        @Override public OutboxDeliveryStatus getDeliveryStatus() { return status; }
        @Override public void setDeliveryStatus(OutboxDeliveryStatus s) { this.status = s; }
        @Override public Instant getDispatchedAt() { return dispatchedAt; }
        @Override public void setDispatchedAt(Instant t) { this.dispatchedAt = t; }
        @Override public int getOutboxRetryCount() { return 0; }
        @Override public void setOutboxRetryCount(int count) { /* not used in test */ }
    }

    private OutboxRouterFanOut outboxRouterFanOut;
    private SwimDeadLetterPort deadLetterService;
    private List<StubEvent> persisted;
    private List<StubEvent> updated;

    @BeforeEach
    void setUp() {
        outboxRouterFanOut = mock(OutboxRouterFanOut.class);
        deadLetterService = mock(SwimDeadLetterPort.class);
        persisted = new ArrayList<>();
        updated = new ArrayList<>();
    }

    private AbstractEventPersistenceService<String, StubEvent> service() {
        return new AbstractEventPersistenceService<>(outboxRouterFanOut, deadLetterService) {
            @Override
            protected StubEvent assembleEntity(ProcessingContext ctx, String event, String contentHash) {
                return new StubEvent(ctx.amqpMessageId(), event);
            }

            @Override
            protected void persistEntity(StubEvent entity) { persisted.add(entity); }

            @Override
            protected void persistEntities(List<StubEvent> entities) { persisted.addAll(entities); }

            @Override
            protected void updateEntity(StubEvent entity) { updated.add(entity); }

            @Override
            protected String getServicePrefix() { return "TEST"; }
        };
    }

    private ProcessingContext ctx(String messageId) {
        return new ProcessingContext("sub-1", "queue-1", messageId, "<xml/>", 0, "inbox-1");
    }

    @Test
    void persistAndDispatch_persistsEntityAndDispatchesToOutbox() {
        var svc = service();

        svc.persistAndDispatch(ctx("msg-1"), "event-payload", "hash-1");

        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().messageId).isEqualTo("msg-1");
        verify(outboxRouterFanOut).route("msg-1", "event-payload");
    }

    @Test
    void persistAndDispatch_sendsToDlqOnPersistenceFailure() {
        var svc = new AbstractEventPersistenceService<String, StubEvent>(outboxRouterFanOut, deadLetterService) {
            @Override
            protected StubEvent assembleEntity(ProcessingContext ctx, String event, String contentHash) {
                return new StubEvent(ctx.amqpMessageId(), event);
            }

            @Override
            protected void persistEntity(StubEvent entity) {
                throw new RuntimeException("db error");
            }

            @Override
            protected void persistEntities(List<StubEvent> entities) { /* not exercised in this test */ }

            @Override
            protected void updateEntity(StubEvent entity) { /* not exercised in this test */ }

            @Override
            protected String getServicePrefix() { return "TEST"; }
        };

        var context = ctx("msg-1");
        assertThatThrownBy(() -> svc.persistAndDispatch(context, "event-payload", "hash-1"))
                .isInstanceOf(ConsumerPersistenceException.class);

        verify(deadLetterService).sendToDeadLetterQueue(any(), any(), any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void persistAndDispatch_handlesOutboxFailureGracefully() {
        doThrow(new RuntimeException("outbox fail")).when(outboxRouterFanOut).route(any(), any());
        var svc = service();

        svc.persistAndDispatch(ctx("msg-1"), "event-payload", "hash-1");

        assertThat(persisted).hasSize(1);
        assertThat(updated).hasSize(1);
        assertThat(updated.getFirst().status).isEqualTo(OutboxDeliveryStatus.PENDING);
        assertThat(updated.getFirst().dispatchedAt).isNull();
    }

    @Test
    void batchPersistAndDispatch_persistsAllAndDispatchesEach() {
        var svc = service();
        List<PreparedEvent<String>> batch = List.of(
                new PreparedEvent<>(ctx("msg-a"), "payload-a", "hash-a"),
                new PreparedEvent<>(ctx("msg-b"), "payload-b", "hash-b")
        );

        svc.batchPersistAndDispatch(batch);

        assertThat(persisted).hasSize(2);
        verify(outboxRouterFanOut).route("msg-a", "payload-a");
        verify(outboxRouterFanOut).route("msg-b", "payload-b");
    }
}
