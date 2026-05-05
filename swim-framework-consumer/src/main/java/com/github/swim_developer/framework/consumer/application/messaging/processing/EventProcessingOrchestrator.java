package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.PreparedEvent;
import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.model.ProcessingOutcome;
import com.github.swim_developer.framework.domain.exception.XmlValidationException;
import com.github.swim_developer.framework.application.model.InterceptorResult;
import com.github.swim_developer.framework.application.model.MessageInterceptorContext;
import com.github.swim_developer.framework.application.port.in.SwimMessageInterceptor;
import com.github.swim_developer.framework.application.port.out.SwimEventExtractor;
import com.github.swim_developer.framework.infrastructure.util.HashUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class EventProcessingOrchestrator<E, P> {

    private final SwimEventProcessorConfig config;
    private final SwimEventParser<P> parser;
    private final SwimEventExtractor<E, P> extractor;
    private final SwimEventValidator<E> validator;
    private final SwimEventFilter<E> filter;
    private final SwimEventPersister<E> persister;
    private final SwimEventProcessorCallbacks<E> callbacks;
    private final MeterRegistry meterRegistry;
    private final List<SwimMessageInterceptor> sortedInterceptors;
    private final Map<String, Timer> processingTimers = new ConcurrentHashMap<>();

    public EventProcessingOrchestrator(EventProcessingOrchestratorDependencies<E, P> deps) {
        this.config = deps.config();
        this.parser = deps.parser();
        this.extractor = deps.extractor();
        this.validator = deps.validator();
        this.filter = deps.filter();
        this.persister = deps.persister();
        this.callbacks = deps.callbacks();
        this.meterRegistry = deps.meterRegistry();
        Instance<SwimMessageInterceptor> interceptorInstances = deps.interceptorInstances();
        if (interceptorInstances == null || interceptorInstances.isUnsatisfied()) {
            this.sortedInterceptors = List.of();
        } else {
            this.sortedInterceptors = interceptorInstances.stream()
                    .sorted(Comparator.comparingInt(SwimMessageInterceptor::order))
                    .toList();
        }
    }

    public Optional<PreparedEvent<E>> validateAndPrepare(ProcessingContext ctx) {
        try {
            if (callbacks.preProcess(ctx)) {
                return Optional.empty();
            }

            Optional<P> parsedOpt = parseAndValidateXml(ctx);
            if (parsedOpt.isEmpty()) {
                return Optional.empty();
            }

            Optional<E> extractedOpt = extractAndValidateEvent(ctx, parsedOpt.get());
            if (extractedOpt.isEmpty()) {
                return Optional.empty();
            }

            E event = extractedOpt.get();

            if (!filter.passesSubscriptionFilter(ctx.subscriptionId(), event)) {
                filter.onFilterMismatch(ctx, event);
                return Optional.empty();
            }

            String contentHash = HashUtil.sha256(ctx.xmlPayload());
            if (config.getIdempotencyCache().isAlreadyProcessed(ctx.subscriptionId(), contentHash)) {
                log.warn("DUPLICATE_DETECTED: MessageId={}, Hash={}, Queue={}, SubId={}",
                        ctx.compositeMessageId(), contentHash, ctx.queueName(), ctx.subscriptionId());
                callbacks.onDuplicateDetected(ctx, contentHash);
                return Optional.empty();
            }

            if (!callbacks.postExtractValidation(ctx, event)) {
                return Optional.empty();
            }

            if (!executeInterceptors(ctx, event)) {
                return Optional.empty();
            }

            return Optional.of(new PreparedEvent<>(ctx, event, contentHash));
        } catch (RuntimeException e) {
            log.error("{} unexpected error during validation - MessageId: {}",
                    config.getServicePrefix(), ctx.amqpMessageId(), e);
            return Optional.empty();
        }
    }

    public ProcessingOutcome processMessage(ProcessingContext ctx) {
        long startTime = System.nanoTime();
        AtomicReference<String> typeLabel = new AtomicReference<>("unknown");

        try {
            Optional<PreparedEvent<E>> prepared = validateAndPrepare(ctx);
            if (prepared.isEmpty()) {
                return ProcessingOutcome.SKIPPED;
            }

            PreparedEvent<E> pe = prepared.get();
            typeLabel.set(extractor.getTypeLabel(pe.event()));
            persister.persistAndDispatch(pe.ctx(), pe.event(), pe.contentHash());
            config.getIdempotencyCache().markAsProcessed(pe.ctx().subscriptionId(), pe.contentHash());
            return ProcessingOutcome.PERSISTED;
        } finally {
            long duration = System.nanoTime() - startTime;
            getProcessingTimer(typeLabel.get()).record(duration, TimeUnit.NANOSECONDS);
        }
    }

    public void batchPersistAndDispatch(List<PreparedEvent<E>> batch) {
        persister.batchPersistAndDispatch(batch);
    }

    public void markBatchAsProcessed(List<PreparedEvent<E>> batch) {
        for (PreparedEvent<E> item : batch) {
            config.getIdempotencyCache().markAsProcessed(item.ctx().subscriptionId(), item.contentHash());
        }
    }

    private Optional<P> parseAndValidateXml(ProcessingContext ctx) {
        try {
            return Optional.of(parser.unmarshalAndValidate(ctx.xmlPayload()));
        } catch (XmlValidationException e) {
            log.error("{} validation failed: {}", config.getServicePrefix(), e.getMessage());
            callbacks.onValidationFailure(ctx, e);
            config.getDeadLetterService().sendToDeadLetterQueue(
                    ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                    ctx.index(), ctx.xmlPayload(), "VALIDATION_ERROR", e);
            return Optional.empty();
        }
    }

    private Optional<E> extractAndValidateEvent(ProcessingContext ctx, P parsed) {
        Optional<E> extracted = safeExtractEvent(parsed);
        if (extracted.isEmpty()) {
            callbacks.onExtractionFailure(ctx, config);
            return Optional.empty();
        }

        E event = extracted.get();

        if (!safeValidateExtractedData(ctx, event)) {
            return Optional.empty();
        }

        return Optional.of(event);
    }

    private Optional<E> safeExtractEvent(P parsed) {
        try {
            return extractor.extractEvent(parsed);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private boolean safeValidateExtractedData(ProcessingContext ctx, E event) {
        try {
            validator.validateExtractedData(ctx, event);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Timer getProcessingTimer(String typeLabel) {
        return processingTimers.computeIfAbsent(typeLabel, t ->
                Timer.builder(config.getServicePrefix() + "_event_processing_seconds")
                        .tag("type", t)
                        .register(meterRegistry));
    }

    @SuppressWarnings("unused")
    private boolean executeInterceptors(ProcessingContext ctx, E event) {
        if (sortedInterceptors.isEmpty()) {
            return true;
        }

        MessageInterceptorContext interceptorCtx = new MessageInterceptorContext(
                ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(), ctx.index());

        for (SwimMessageInterceptor interceptor : sortedInterceptors) {
            try {
                InterceptorResult result = interceptor.intercept(ctx.xmlPayload(), interceptorCtx);
                if (result == InterceptorResult.SKIP) {
                    log.warn("INTERCEPTOR_SKIP: Interceptor={}, MessageId={}, Queue={}, SubId={}",
                            interceptor.getClass().getSimpleName(), ctx.compositeMessageId(),
                            ctx.queueName(), ctx.subscriptionId());
                    return false;
                }
                if (result == InterceptorResult.REJECT) {
                    log.warn("{} interceptor {} REJECTED - MessageId: {}",
                            config.getServicePrefix(), interceptor.getClass().getSimpleName(), ctx.amqpMessageId());
                    config.getDeadLetterService().sendToDeadLetterQueue(
                            ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                            ctx.index(), ctx.xmlPayload(), "INTERCEPTOR_REJECTED",
                            new RuntimeException("Rejected by " + interceptor.getClass().getSimpleName()));
                    return false;
                }
            } catch (Exception e) {
                log.error("{} interceptor {} threw exception - MessageId: {}",
                        config.getServicePrefix(), interceptor.getClass().getSimpleName(), ctx.amqpMessageId(), e);
                config.getDeadLetterService().sendToDeadLetterQueue(
                        ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                        ctx.index(), ctx.xmlPayload(), "INTERCEPTOR_ERROR", e);
                return false;
            }
        }
        return true;
    }
}
