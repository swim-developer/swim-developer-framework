package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;

import java.util.List;

public interface SwimOutboxEventStorePort {

    @SuppressWarnings("java:S1452")
    List<? extends SwimOutboxEvent> findPendingOutboxEvents(int batchSize);

    void updateOutboxEvent(SwimOutboxEvent event);
}
