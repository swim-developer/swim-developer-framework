package com.github.swim_developer.framework.consumer.infrastructure.in.inbox;

import com.github.swim_developer.framework.application.model.InboxStatus;
import com.github.swim_developer.framework.application.model.ProcessingOutcome;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractInboxEventConsumerTest {

    record InboxDoc(String id, String payload) {}

    private List<InboxStatus> capturedStatuses;
    private List<Integer> capturedProcessedCounts;
    private AbstractInboxEventConsumer<InboxDoc> consumer;

    @BeforeEach
    void setUp() {
        capturedStatuses = new ArrayList<>();
        capturedProcessedCounts = new ArrayList<>();
    }

    private AbstractInboxEventConsumer<InboxDoc> consumerReturning(ProcessingOutcome outcome) {
        return new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) {
                return new InboxDoc(inboxId, "payload-" + inboxId);
            }

            @Override
            protected String getRawPayload(InboxDoc inbox) {
                return inbox.payload();
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String message, int index) {
                return outcome;
            }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int processedCount) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(processedCount);
            }
        };
    }

    private AbstractInboxEventConsumer<InboxDoc> consumerThrowing() {
        return new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) {
                return new InboxDoc(inboxId, "payload-" + inboxId);
            }

            @Override
            protected String getRawPayload(InboxDoc inbox) {
                return inbox.payload();
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String message, int index) {
                throw new RuntimeException("processing error");
            }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int processedCount) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(processedCount);
            }
        };
    }

    @Test
    void processInboxEvent_resolvesInboxAndProcesses() {
        consumer = consumerReturning(ProcessingOutcome.PERSISTED);

        consumer.processInboxEvent("inbox-1");

        assertThat(capturedStatuses).containsExactly(InboxStatus.COMPLETED);
        assertThat(capturedProcessedCounts).containsExactly(1);
    }

    @Test
    void processInboxEvent_logsErrorWhenInboxNotFound() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) {
                return null;
            }

            @Override
            protected String getRawPayload(InboxDoc inbox) { return null; }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String msg, int idx) { return null; }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int count) {
                capturedStatuses.add(status);
            }
        };

        consumer.processInboxEvent("missing");

        assertThat(capturedStatuses).isEmpty();
    }

    @Test
    void processInboxEvent_logsErrorWhenResolveThrows() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) {
                throw new RuntimeException("db error");
            }

            @Override
            protected String getRawPayload(InboxDoc inbox) { return null; }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String msg, int idx) { return null; }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int count) {
                capturedStatuses.add(status);
            }
        };

        consumer.processInboxEvent("bad-id");

        assertThat(capturedStatuses).isEmpty();
    }

    @Test
    void process_singleMessage_persisted_setsCompletedStatus() {
        consumer = consumerReturning(ProcessingOutcome.PERSISTED);
        consumer.process(new InboxDoc("id-1", "payload"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.COMPLETED);
        assertThat(capturedProcessedCounts).containsExactly(1);
    }

    @Test
    void process_singleMessage_skipped_setsCompletedWithZeroCount() {
        consumer = consumerReturning(ProcessingOutcome.SKIPPED);
        consumer.process(new InboxDoc("id-1", "payload"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.COMPLETED);
        assertThat(capturedProcessedCounts).containsExactly(0);
    }

    @Test
    void process_singleMessage_throws_setsFailedStatus() {
        consumer = consumerThrowing();
        consumer.process(new InboxDoc("id-1", "payload"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.FAILED);
        assertThat(capturedProcessedCounts).containsExactly(0);
    }

    @Test
    void process_multipleMessages_allPersisted_setsCompleted() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) { return new InboxDoc(inboxId, "p"); }

            @Override
            protected String getRawPayload(InboxDoc inbox) { return inbox.payload(); }

            @Override
            protected List<String> extractMessages(String rawPayload) {
                return List.of("msg1", "msg2", "msg3");
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String message, int index) {
                return ProcessingOutcome.PERSISTED;
            }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int processedCount) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(processedCount);
            }
        };

        consumer.process(new InboxDoc("id-batch", "p"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.COMPLETED);
        assertThat(capturedProcessedCounts).containsExactly(3);
    }

    @Test
    void process_multipleMessages_someFailed_setsPartialFailed() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) { return new InboxDoc(inboxId, "p"); }

            @Override
            protected String getRawPayload(InboxDoc inbox) { return inbox.payload(); }

            @Override
            protected List<String> extractMessages(String rawPayload) {
                return List.of("msg1", "msg2");
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String message, int index) {
                if (index == 1) throw new RuntimeException("fail");
                return ProcessingOutcome.PERSISTED;
            }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int processedCount) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(processedCount);
            }
        };

        consumer.process(new InboxDoc("id-partial", "p"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.PARTIAL_FAILED);
        assertThat(capturedProcessedCounts).containsExactly(1);
    }

    @Test
    void process_multipleMessages_allFailed_setsFailed() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) { return new InboxDoc(inboxId, "p"); }

            @Override
            protected String getRawPayload(InboxDoc inbox) { return inbox.payload(); }

            @Override
            protected List<String> extractMessages(String rawPayload) {
                return List.of("msg1", "msg2");
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String message, int index) {
                throw new RuntimeException("fail " + index);
            }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int processedCount) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(processedCount);
            }
        };

        consumer.process(new InboxDoc("id-all-fail", "p"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.FAILED);
        assertThat(capturedProcessedCounts).containsExactly(0);
    }

    @Test
    void process_catchesTopLevelException_setsFailedStatus() {
        consumer = new AbstractInboxEventConsumer<>() {
            @Override
            protected InboxDoc resolveInbox(String inboxId) { return new InboxDoc(inboxId, "p"); }

            @Override
            protected String getRawPayload(InboxDoc inbox) {
                throw new RuntimeException("getRawPayload failed");
            }

            @Override
            protected ProcessingOutcome processSingleMessage(InboxDoc inbox, String msg, int idx) { return null; }

            @Override
            protected void updateInboxFinalStatus(InboxDoc inbox, InboxStatus status, int count) {
                capturedStatuses.add(status);
                capturedProcessedCounts.add(count);
            }
        };

        consumer.process(new InboxDoc("id-throw", "p"));

        assertThat(capturedStatuses).containsExactly(InboxStatus.FAILED);
        assertThat(capturedProcessedCounts).containsExactly(0);
    }

    @Test
    void extractMessages_defaultImplementation_returnsSingleElement() {
        consumer = consumerReturning(ProcessingOutcome.PERSISTED);

        List<String> result = consumer.extractMessages("raw-payload");

        assertThat(result).containsExactly("raw-payload");
    }
}
