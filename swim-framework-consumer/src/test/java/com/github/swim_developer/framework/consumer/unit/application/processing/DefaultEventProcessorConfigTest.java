package com.github.swim_developer.framework.consumer.unit.application.processing;

import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;
import com.github.swim_developer.framework.consumer.application.messaging.processing.DefaultEventProcessorConfig;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class DefaultEventProcessorConfigTest {

    private final SwimIdempotencyPort idempotencyCache = mock(SwimIdempotencyPort.class);
    private final SwimDeadLetterPort deadLetterService = mock(SwimDeadLetterPort.class);

    @Test
    void getServicePrefix_returnsConfiguredValue() {
        DefaultEventProcessorConfig config = new DefaultEventProcessorConfig(
                "dnotam", idempotencyCache, deadLetterService);

        assertThat(config.getServicePrefix()).isEqualTo("dnotam");
    }

    @Test
    void getIdempotencyCache_returnsConfiguredPort() {
        DefaultEventProcessorConfig config = new DefaultEventProcessorConfig(
                "dnotam", idempotencyCache, deadLetterService);

        assertThat(config.getIdempotencyCache()).isSameAs(idempotencyCache);
    }

    @Test
    void getDeadLetterService_returnsConfiguredPort() {
        DefaultEventProcessorConfig config = new DefaultEventProcessorConfig(
                "dnotam", idempotencyCache, deadLetterService);

        assertThat(config.getDeadLetterService()).isSameAs(deadLetterService);
    }

    @Test
    void allGetters_consistentAcrossMultipleCalls() {
        DefaultEventProcessorConfig config = new DefaultEventProcessorConfig(
                "ed254", idempotencyCache, deadLetterService);

        assertThat(config.getServicePrefix()).isEqualTo("ed254");
        assertThat(config.getServicePrefix()).isEqualTo("ed254");
        assertThat(config.getIdempotencyCache()).isSameAs(idempotencyCache);
        assertThat(config.getDeadLetterService()).isSameAs(deadLetterService);
    }
}
