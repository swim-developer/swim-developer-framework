package com.github.swim_developer.framework.domain.model;

import java.time.Instant;

public interface SwimProviderEvent {

    String getEventId();

    String getPayload();

    EventStatus getStatus();

    void setStatus(EventStatus status);

    int getRetryCount();

    void setRetryCount(int count);

    int getDeliveredCount();

    void setDeliveredCount(int count);

    Instant getProcessedAt();

    void setProcessedAt(Instant processedAt);
}
