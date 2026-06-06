package com.github.swim_developer.framework.domain.model;

import com.github.swim_developer.framework.application.model.OutboxDeliveryStatus;

import java.time.Instant;

public interface SwimOutboxEvent {

    String getMessageId();

    String getRawPayload();

    OutboxDeliveryStatus getDeliveryStatus();

    void setDeliveryStatus(OutboxDeliveryStatus status);

    Instant getDispatchedAt();

    void setDispatchedAt(Instant time);

    int getOutboxRetryCount();

    void setOutboxRetryCount(int count);

    default String getSubscriptionId() { return null; }

    default String getQueueName() { return null; }

    default Instant getReceivedAt() { return null; }
}
