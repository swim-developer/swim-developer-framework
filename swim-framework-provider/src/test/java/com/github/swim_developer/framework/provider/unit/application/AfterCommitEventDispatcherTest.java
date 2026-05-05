package com.github.swim_developer.framework.provider.unit.application;

import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.application.messaging.AfterCommitEventDispatcher;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import jakarta.transaction.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AfterCommitEventDispatcherTest {

    private HandoffCache handoffCache;
    private Vertx vertx;
    private EventBus eventBus;
    private AfterCommitEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        handoffCache = mock(HandoffCache.class);
        vertx = mock(Vertx.class);
        eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        dispatcher = new AfterCommitEventDispatcher("evt-1", "payload", handoffCache, vertx, "swim.outbox.dnotam");
    }

    @Test
    void beforeCompletion_doesNothing() {
        dispatcher.beforeCompletion();

        verifyNoInteractions(handoffCache, vertx);
    }

    @Test
    void afterCompletion_committed_putsInCacheAndPublishesToBus() {
        dispatcher.afterCompletion(Status.STATUS_COMMITTED);

        verify(handoffCache).put("evt-1", "payload");
        verify(eventBus).publish("swim.outbox.dnotam", "evt-1");
    }

    @Test
    void afterCompletion_rolledBack_doesNotPublish() {
        dispatcher.afterCompletion(Status.STATUS_ROLLEDBACK);

        verifyNoInteractions(handoffCache);
        verifyNoInteractions(eventBus);
    }

    @Test
    void afterCompletion_unknownStatus_doesNotPublish() {
        dispatcher.afterCompletion(Status.STATUS_UNKNOWN);

        verifyNoInteractions(handoffCache);
        verifyNoInteractions(eventBus);
    }
}
