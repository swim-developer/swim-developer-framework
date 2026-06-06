package com.github.swim_developer.framework.consumer.infrastructure.out.dlq;

import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxRouterFanOut;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(TestNameLoggerExtension.class)
class AbstractDeadLetterServiceTest {

    private OutboxRouterFanOut fanOut;
    private SimpleMeterRegistry meterRegistry;
    private StubDeadLetterService service;

    @BeforeEach
    void setUp() {
        fanOut = mock(OutboxRouterFanOut.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new StubDeadLetterService(meterRegistry, fanOut);
        service.initDlqMetrics();
    }

    @Test
    void initDlqMetrics_registersCountersForAllKnownErrorTypes() {
        assertThat(meterRegistry.find("test_events_dlq_total").tag("error_type", "VALIDATION_ERROR").counter())
                .isNotNull();
        assertThat(meterRegistry.find("test_events_dlq_total").tag("error_type", "UNKNOWN_ERROR").counter())
                .isNotNull();
    }

    @Test
    void sendToDeadLetterQueue_persistsAndRoutesMessage() {
        service.sendToDeadLetterQueue("sub-1", "queue", "msg-1", 0, "<xml/>", "VALIDATION_ERROR",
                new RuntimeException("fail"));

        verify(fanOut).sendToDeadLetterQueue("msg-1#0", "<xml/>");
        assertThat(service.lastParams).isNotNull();
        assertThat(service.lastParams.errorType()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void sendToDeadLetterQueue_doesNotThrow_whenPersistFails() {
        service.persistShouldThrow = true;

        assertThatNoException().isThrownBy(() ->
                service.sendToDeadLetterQueue("sub-1", "queue", "msg-1", 0, "<xml/>",
                        "VALIDATION_ERROR", new RuntimeException("fail")));
    }

    @Test
    void sendToDeadLetterQueue_doesNotThrow_whenRouterThrows() {
        doThrow(new RuntimeException("outbox down")).when(fanOut).sendToDeadLetterQueue(any(), any());

        assertThatNoException().isThrownBy(() ->
                service.sendToDeadLetterQueue("sub-1", "queue", "msg-1", 0, "<xml/>",
                        "VALIDATION_ERROR", new RuntimeException("fail")));
    }

    @Test
    void sendMessageToDeadLetterQueue_persistsAndRoutesMessage() {
        service.sendMessageToDeadLetterQueue("msg-2", "sub-1", "queue", "<xml/>",
                Instant.now(), "PERSISTENCE_ERROR");

        verify(fanOut).sendToDeadLetterQueue("msg-2", "<xml/>");
        assertThat(service.lastParams.errorType()).isEqualTo("PERSISTENCE_ERROR");
    }

    @Test
    void sendMessageToDeadLetterQueue_doesNotThrow_whenRouterThrows() {
        doThrow(new RuntimeException("outbox down")).when(fanOut).sendToDeadLetterQueue(any(), any());

        assertThatNoException().isThrownBy(() ->
                service.sendMessageToDeadLetterQueue("msg-2", "sub-1", "queue", "<xml/>",
                        Instant.now(), "PERSISTENCE_ERROR"));
    }

    @Test
    void sendEventToDeadLetterQueue_routesWithoutPersist() {
        service.sendEventToDeadLetterQueue("msg-3", "<data/>", "KAFKA_MAX_RETRIES_EXCEEDED");

        verify(fanOut).sendToDeadLetterQueue("msg-3", "<data/>");
        assertThat(service.lastParams).isNull();
    }

    @Test
    void sendEventToDeadLetterQueue_doesNotThrow_whenRouterThrows() {
        doThrow(new RuntimeException("outbox down")).when(fanOut).sendToDeadLetterQueue(any(), any());

        assertThatNoException().isThrownBy(() ->
                service.sendEventToDeadLetterQueue("msg-3", "<data/>", "UNKNOWN_ERROR"));
    }

    @Test
    void incrementDlqCounter_fallsBackToUnknownError_forUnregisteredType() {
        double before = meterRegistry.find("test_events_dlq_total")
                .tag("error_type", "UNKNOWN_ERROR").counter().count();

        service.incrementDlqCounter("TOTALLY_UNKNOWN_TYPE");

        double after = meterRegistry.find("test_events_dlq_total")
                .tag("error_type", "UNKNOWN_ERROR").counter().count();
        assertThat(after).isEqualTo(before + 1);
    }

    private static org.assertj.core.api.AbstractObjectAssert<?, Object> assertThat(Object o) {
        return org.assertj.core.api.Assertions.assertThat(o);
    }

    private static class StubDeadLetterService extends AbstractDeadLetterService {
        private final OutboxRouterFanOut fanOut;
        DeadLetterParams lastParams;
        boolean persistShouldThrow;

        StubDeadLetterService(io.micrometer.core.instrument.MeterRegistry registry, OutboxRouterFanOut fanOut) {
            super(registry);
            this.fanOut = fanOut;
        }

        @Override
        protected OutboxRouterFanOut getRouterFanOut() {
            return fanOut;
        }

        @Override
        protected String getServiceName() {
            return "test";
        }

        @Override
        protected void persist(DeadLetterParams params) {
            if (persistShouldThrow) {
                throw new RuntimeException("persist failed");
            }
            this.lastParams = params;
        }
    }
}
