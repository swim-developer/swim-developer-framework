package com.github.swim_developer.framework.consumer.unit.messaging.outbox;

import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxDispatcher;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class OutboxDispatcherTest {

    private HandoffCache handoffCache;
    private Vertx vertx;
    private EventBus eventBus;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        handoffCache = mock(HandoffCache.class);
        vertx = mock(Vertx.class);
        eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        dispatcher = new OutboxDispatcher(handoffCache, vertx);
    }

    @Test
    void dispatch_storesEventInHandoffCache() {
        ObjectId eventId = new ObjectId();
        Object event = new Object();
        String address = "dnotam.events.outbox";

        dispatcher.dispatch(eventId, event, address);

        verify(handoffCache).put(eventId.toHexString(), event);
    }

    @Test
    void dispatch_publishesEventIdToVertxBus() {
        ObjectId eventId = new ObjectId();
        Object event = new Object();
        String address = "dnotam.events.outbox";

        dispatcher.dispatch(eventId, event, address);

        verify(eventBus).publish(address, eventId.toHexString());
    }

    @Test
    void dispatch_usesHexStringAsKey() {
        ObjectId eventId = new ObjectId();
        Object event = "test-payload";
        String address = "ed254.events.outbox";
        String expectedKey = eventId.toHexString();

        dispatcher.dispatch(eventId, event, address);

        verify(handoffCache).put(expectedKey, event);
        verify(eventBus).publish(address, expectedKey);
    }

    @Test
    void dispatch_withDifferentAddresses_publishesToCorrectAddress() {
        ObjectId eventId = new ObjectId();
        Object event = "payload";

        dispatcher.dispatch(eventId, event, "address.one");
        verify(eventBus).publish("address.one", eventId.toHexString());
    }
}
