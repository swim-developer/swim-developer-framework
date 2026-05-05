package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.ProcessingContext;

public interface SwimEventFilter<E> {

    boolean passesSubscriptionFilter(String subscriptionId, E event);

    void onFilterMismatch(ProcessingContext ctx, E event);
}
