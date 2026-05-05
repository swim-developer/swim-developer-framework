package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.port.in.SwimMessageInterceptor;
import com.github.swim_developer.framework.application.port.out.SwimEventExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.inject.Instance;

public record EventProcessingOrchestratorDependencies<E, P>(
        SwimEventProcessorConfig config,
        SwimEventParser<P> parser,
        SwimEventExtractor<E, P> extractor,
        SwimEventValidator<E> validator,
        SwimEventFilter<E> filter,
        SwimEventPersister<E> persister,
        SwimEventProcessorCallbacks<E> callbacks,
        MeterRegistry meterRegistry,
        Instance<SwimMessageInterceptor> interceptorInstances) {}
