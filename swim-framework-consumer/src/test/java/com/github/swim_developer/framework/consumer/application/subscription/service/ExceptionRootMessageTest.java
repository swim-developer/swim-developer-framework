package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class ExceptionRootMessageTest {

    @Test
    void format_returnsSimpleClassNameAndMessage() {
        RuntimeException ex = new RuntimeException("connection refused");
        assertThat(ExceptionRootMessage.format(ex))
                .isEqualTo("RuntimeException: connection refused");
    }

    @Test
    void format_traversesToRootCause() {
        IllegalStateException root = new IllegalStateException("root cause message");
        RuntimeException wrapper = new RuntimeException("wrapper message", root);
        RuntimeException outerWrapper = new RuntimeException("outer message", wrapper);

        assertThat(ExceptionRootMessage.format(outerWrapper))
                .isEqualTo("IllegalStateException: root cause message");
    }

    @Test
    void format_withNoCause_returnsDirectException() {
        NullPointerException ex = new NullPointerException("npe message");
        assertThat(ExceptionRootMessage.format(ex))
                .isEqualTo("NullPointerException: npe message");
    }

    @Test
    void format_withNullMessage_includesNullText() {
        RuntimeException ex = new RuntimeException((String) null);
        String result = ExceptionRootMessage.format(ex);
        assertThat(result).startsWith("RuntimeException:");
    }

    @Test
    void format_withDeepChain_traversesAllTheWayToRoot() {
        Exception level4 = new IllegalArgumentException("deepest");
        Exception level3 = new RuntimeException("level3", level4);
        Exception level2 = new RuntimeException("level2", level3);
        Exception level1 = new RuntimeException("level1", level2);

        assertThat(ExceptionRootMessage.format(level1))
                .isEqualTo("IllegalArgumentException: deepest");
    }
}
