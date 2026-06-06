package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.domain.exception.EventProcessingException;

public interface SwimEventProcessorCallbacks<E> {

    default boolean preProcess(ProcessingContext ctx) {
        return false;
    }

    default void onValidationFailure(ProcessingContext ctx, Exception e) {
    }

    default void onDuplicateDetected(ProcessingContext ctx, String contentHash) {
    }

    default boolean postExtractValidation(ProcessingContext ctx, E event) {
        return true;
    }

    default void onExtractionFailure(ProcessingContext ctx, SwimEventProcessorConfig config) {
        config.getDeadLetterService().sendToDeadLetterQueue(
                ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                ctx.index(), ctx.xmlPayload(), "EXTRACTION_ERROR",
                new IllegalArgumentException("Failed to extract event metadata"));
        throw new EventProcessingException("Failed to extract " + config.getServicePrefix() + " event");
    }
}
