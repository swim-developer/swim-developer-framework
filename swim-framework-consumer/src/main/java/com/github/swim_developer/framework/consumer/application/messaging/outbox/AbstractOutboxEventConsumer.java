package com.github.swim_developer.framework.consumer.application.messaging.outbox;

import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public abstract class AbstractOutboxEventConsumer<E extends SwimOutboxEvent> {

    private final int maxKafkaRetries;

    protected AbstractOutboxEventConsumer() {
        this.maxKafkaRetries = 3;
    }

    protected AbstractOutboxEventConsumer(int maxKafkaRetries) {
        this.maxKafkaRetries = maxKafkaRetries;
    }

    public void processOutboxEvent(String eventId) {
        E event = resolveEvent(eventId);
        if (event == null) {
            log.error("Event not found: {}", eventId);
            return;
        }

        if (!isPending(event)) {
            return;
        }

        sendAndUpdateStatus(event);
    }

    protected void sendAndUpdateStatus(E event) {
        try {
            getRouterFanOut().route(event.getMessageId(), event.getRawPayload());
            markAsSent(event);
        } catch (Exception e) {
            log.error("Failed to send to outbox - EventId: {}", getEventId(event), e);
            incrementRetryCount(event);

            if (exceedsMaxRetries(event)) {
                getRouterFanOut().sendToDeadLetterQueue(event.getMessageId(), event.getRawPayload());
                markAsFailed(event);
            }
        }
    }

    protected abstract E resolveEvent(String eventId);

    protected abstract OutboxRouterFanOut getRouterFanOut();

    protected abstract String getEventId(E event);

    protected abstract void updateEvent(E event);

    protected boolean isPending(E event) {
        return event.getDeliveryStatus() == com.github.swim_developer.framework.application.model.OutboxDeliveryStatus.PENDING;
    }

    protected void markAsSent(E event) {
        event.setDeliveryStatus(com.github.swim_developer.framework.application.model.OutboxDeliveryStatus.SENT);
        event.setDispatchedAt(Instant.now());
        updateEvent(event);
    }

    protected void markAsFailed(E event) {
        event.setDeliveryStatus(com.github.swim_developer.framework.application.model.OutboxDeliveryStatus.FAILED);
        updateEvent(event);
    }

    protected void incrementRetryCount(E event) {
        event.setOutboxRetryCount(event.getOutboxRetryCount() + 1);
        updateEvent(event);
    }

    protected boolean exceedsMaxRetries(E event) {
        return event.getOutboxRetryCount() >= maxKafkaRetries;
    }
}
