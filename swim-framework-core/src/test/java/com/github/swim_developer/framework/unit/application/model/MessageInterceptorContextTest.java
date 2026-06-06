package com.github.swim_developer.framework.unit.application.model;

import com.github.swim_developer.framework.application.model.MessageInterceptorContext;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class MessageInterceptorContextTest {

    private MessageInterceptorContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new MessageInterceptorContext("sub-1", "q-1", "msg-001", 2);
    }

    @Test
    void constructorFields_areAccessible() {
        assertThat(ctx.getSubscriptionId()).isEqualTo("sub-1");
        assertThat(ctx.getQueueName()).isEqualTo("q-1");
        assertThat(ctx.getAmqpMessageId()).isEqualTo("msg-001");
        assertThat(ctx.getMessageIndex()).isEqualTo(2);
    }

    @Test
    void setAttribute_storesValue() {
        ctx.setAttribute("key1", "value1");
        assertThat((String) ctx.getAttribute("key1")).isEqualTo("value1");
    }

    @Test
    void getAttribute_returnsNullForMissingKey() {
        assertThat((Object) ctx.getAttribute("nonexistent")).isNull();
    }

    @Test
    void setAttribute_overwritesPreviousValue() {
        ctx.setAttribute("k", "first");
        ctx.setAttribute("k", "second");
        assertThat((String) ctx.getAttribute("k")).isEqualTo("second");
    }

    @Test
    void setAttribute_supportsTypedAttributes() {
        ctx.setAttribute("count", 42);
        assertThat((Integer) ctx.getAttribute("count")).isEqualTo(42);
    }
}
