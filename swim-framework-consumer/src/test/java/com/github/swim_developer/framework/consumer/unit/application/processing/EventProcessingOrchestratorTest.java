package com.github.swim_developer.framework.consumer.unit.application.processing;

import com.github.swim_developer.framework.application.model.PreparedEvent;
import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.model.ProcessingOutcome;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimEventExtractor;
import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;
import com.github.swim_developer.framework.consumer.application.messaging.processing.*;
import com.github.swim_developer.framework.domain.exception.XmlValidationException;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(TestNameLoggerExtension.class)
@SuppressWarnings("unchecked")
class EventProcessingOrchestratorTest {

    private static final String SUB_ID = "sub-1";
    private static final String QUEUE = "DNOTAM.v1.client.sub-1";
    private static final String MSG_ID = "msg-abc";
    private static final String XML = "<AIXMBasicMessage/>";

    private SwimEventParser<Object> parser;
    private SwimEventExtractor<Object, Object> extractor;
    private SwimEventValidator<Object> validator;
    private SwimEventFilter<Object> filter;
    private SwimEventPersister<Object> persister;
    private SwimEventProcessorCallbacks<Object> callbacks;
    private SwimIdempotencyPort idempotencyPort;
    private SwimDeadLetterPort deadLetterPort;

    private EventProcessingOrchestrator<Object, Object> orchestrator;

    @BeforeEach
    void setUp() {
        parser = mock(SwimEventParser.class);
        extractor = mock(SwimEventExtractor.class);
        validator = mock(SwimEventValidator.class);
        filter = mock(SwimEventFilter.class);
        persister = mock(SwimEventPersister.class);
        callbacks = mock(SwimEventProcessorCallbacks.class);
        idempotencyPort = mock(SwimIdempotencyPort.class);
        deadLetterPort = mock(SwimDeadLetterPort.class);

        SwimEventProcessorConfig config = new SwimEventProcessorConfig() {
            @Override
            public String getServicePrefix() {
                return "dnotam";
            }

            @Override
            public SwimIdempotencyPort getIdempotencyCache() {
                return idempotencyPort;
            }

            @Override
            public SwimDeadLetterPort getDeadLetterService() {
                return deadLetterPort;
            }
        };

        orchestrator = new EventProcessingOrchestrator<>(
                new EventProcessingOrchestratorDependencies<>(config, parser, extractor, validator, filter, persister,
                        callbacks, new SimpleMeterRegistry(), null));
    }

    @Test
    void processMessage_returnsSkipped_whenPreProcessInterceptsMessage() {
        when(callbacks.preProcess(any())).thenReturn(true);

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verifyNoInteractions(parser, persister, idempotencyPort);
    }

    @Test
    void processMessage_returnsPersisted_onHappyPath() {
        Object parsed = new Object();
        Object event = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.of(event));
        when(extractor.getTypeLabel(event)).thenReturn("RWY.CLS");
        when(filter.passesSubscriptionFilter(SUB_ID, event)).thenReturn(true);
        when(idempotencyPort.isAlreadyProcessed(eq(SUB_ID), anyString())).thenReturn(false);
        when(callbacks.postExtractValidation(any(), eq(event))).thenReturn(true);

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.PERSISTED);
        verify(persister).persistAndDispatch(any(), eq(event), anyString());
        verify(idempotencyPort).markAsProcessed(eq(SUB_ID), anyString());
    }

    @Test
    void processMessage_returnsSkipped_andSendsToDlq_whenXmlValidationFails() {
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenThrow(new XmlValidationException("Invalid AIXM"));

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verify(deadLetterPort).sendToDeadLetterQueue(
                eq(SUB_ID), eq(QUEUE), eq(MSG_ID), eq(0), eq(XML),
                eq("VALIDATION_ERROR"), any(XmlValidationException.class));
        verifyNoInteractions(persister);
    }

    @Test
    void processMessage_returnsSkipped_whenEventExtractionReturnsEmpty() {
        Object parsed = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("extraction failed"))
                .when(callbacks).onExtractionFailure(any(), any());

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verifyNoInteractions(persister, idempotencyPort);
    }

    @Test
    void processMessage_returnsSkipped_whenSubscriptionFilterRejects() {
        Object parsed = new Object();
        Object event = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.of(event));
        when(callbacks.postExtractValidation(any(), eq(event))).thenReturn(true);
        when(filter.passesSubscriptionFilter(SUB_ID, event)).thenReturn(false);

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verify(filter).onFilterMismatch(any(), eq(event));
        verifyNoInteractions(persister, idempotencyPort);
    }

    @Test
    void processMessage_returnsSkipped_andCallsDuplicateCallback_whenDuplicateDetected() {
        Object parsed = new Object();
        Object event = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.of(event));
        when(filter.passesSubscriptionFilter(SUB_ID, event)).thenReturn(true);
        when(callbacks.postExtractValidation(any(), eq(event))).thenReturn(true);
        when(idempotencyPort.isAlreadyProcessed(eq(SUB_ID), anyString())).thenReturn(true);

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verify(callbacks).onDuplicateDetected(any(), anyString());
        verifyNoInteractions(persister);
        verify(idempotencyPort, never()).markAsProcessed(any(), any());
    }

    @Test
    void processMessage_returnsSkipped_whenPostExtractValidationFails() {
        Object parsed = new Object();
        Object event = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.of(event));
        when(callbacks.postExtractValidation(any(), eq(event))).thenReturn(false);

        ProcessingOutcome outcome = orchestrator.processMessage(ctx());

        assertThat(outcome).isEqualTo(ProcessingOutcome.SKIPPED);
        verifyNoInteractions(persister, idempotencyPort);
    }

    @Test
    void validateAndPrepare_returnsPreparedEventWithContentHash_onHappyPath() {
        Object parsed = new Object();
        Object event = new Object();
        when(callbacks.preProcess(any())).thenReturn(false);
        when(parser.unmarshalAndValidate(any())).thenReturn(parsed);
        when(extractor.extractEvent(parsed)).thenReturn(Optional.of(event));
        when(filter.passesSubscriptionFilter(SUB_ID, event)).thenReturn(true);
        when(idempotencyPort.isAlreadyProcessed(eq(SUB_ID), anyString())).thenReturn(false);
        when(callbacks.postExtractValidation(any(), eq(event))).thenReturn(true);

        Optional<PreparedEvent<Object>> result = orchestrator.validateAndPrepare(ctx());

        assertThat(result).isPresent();
        assertThat(result.get().event()).isSameAs(event);
        assertThat(result.get().contentHash()).isNotBlank();
        assertThat(result.get().ctx().subscriptionId()).isEqualTo(SUB_ID);
    }

    @Test
    void markBatchAsProcessed_marksAllItemsInIdempotencyCache() {
        Object event = new Object();
        ProcessingContext ctx1 = new ProcessingContext(SUB_ID, QUEUE, "msg-1", XML, 0, "inbox-1");
        ProcessingContext ctx2 = new ProcessingContext(SUB_ID, QUEUE, "msg-2", XML, 0, "inbox-2");
        PreparedEvent<Object> pe1 = new PreparedEvent<>(ctx1, event, "hash-1");
        PreparedEvent<Object> pe2 = new PreparedEvent<>(ctx2, event, "hash-2");

        orchestrator.markBatchAsProcessed(List.of(pe1, pe2));

        verify(idempotencyPort).markAsProcessed(SUB_ID, "hash-1");
        verify(idempotencyPort).markAsProcessed(SUB_ID, "hash-2");
    }

    private static ProcessingContext ctx() {
        return new ProcessingContext(SUB_ID, QUEUE, MSG_ID, XML, 0, "inbox-1");
    }
}
