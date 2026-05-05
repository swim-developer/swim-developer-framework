package com.github.swim_developer.framework.unit.application.model;

import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class ProcessingContextTest {

    @Test
    void compositeMessageId_combinesAmqpIdAndIndex() {
        var ctx = new ProcessingContext("sub-1", "queue-1", "amqp-abc", "<xml/>", 3, "inbox-x");
        assertThat(ctx.compositeMessageId()).isEqualTo("amqp-abc#3");
    }

    @Test
    void compositeMessageId_indexZeroProducesCorrectId() {
        var ctx = new ProcessingContext("sub-1", "queue-1", "msg-001", "<xml/>", 0, "inbox-1");
        assertThat(ctx.compositeMessageId()).isEqualTo("msg-001#0");
    }

    @Test
    void recordFields_areAccessibleViaAccessors() {
        var ctx = new ProcessingContext("s1", "q1", "m1", "<payload/>", 7, "ix1");
        assertThat(ctx.subscriptionId()).isEqualTo("s1");
        assertThat(ctx.queueName()).isEqualTo("q1");
        assertThat(ctx.amqpMessageId()).isEqualTo("m1");
        assertThat(ctx.xmlPayload()).isEqualTo("<payload/>");
        assertThat(ctx.index()).isEqualTo(7);
        assertThat(ctx.inboxId()).isEqualTo("ix1");
    }
}
