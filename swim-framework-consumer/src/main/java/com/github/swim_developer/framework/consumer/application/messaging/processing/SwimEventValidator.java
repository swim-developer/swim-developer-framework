package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.ProcessingContext;

public interface SwimEventValidator<E> {

    void validateExtractedData(ProcessingContext ctx, E event);
}
