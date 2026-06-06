package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.PreparedEvent;
import com.github.swim_developer.framework.application.model.ProcessingContext;

import java.util.List;

public interface SwimEventPersister<E> {

    void persistAndDispatch(ProcessingContext ctx, E event, String contentHash);

    default void batchPersistAndDispatch(List<PreparedEvent<E>> batch) {
        for (PreparedEvent<E> item : batch) {
            persistAndDispatch(item.ctx(), item.event(), item.contentHash());
        }
    }
}
