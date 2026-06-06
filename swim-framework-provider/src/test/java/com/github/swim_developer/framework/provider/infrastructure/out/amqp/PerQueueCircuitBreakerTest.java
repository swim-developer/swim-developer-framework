package com.github.swim_developer.framework.provider.infrastructure.out.amqp;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class PerQueueCircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_DURATION_MS = 60_000;
    private static final int SUCCESS_THRESHOLD = 2;

    private PerQueueCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new PerQueueCircuitBreaker(FAILURE_THRESHOLD, OPEN_DURATION_MS, SUCCESS_THRESHOLD);
    }

    @Test
    void isOpen_returnsFalse_forUnknownQueue() {
        assertThat(breaker.isOpen("queue-unknown")).isFalse();
    }

    @Test
    void isOpen_returnsFalse_beforeThresholdReached() {
        breaker.recordFailure("queue-a");
        breaker.recordFailure("queue-a");

        assertThat(breaker.isOpen("queue-a")).isFalse();
    }

    @Test
    void isOpen_returnsTrue_afterThresholdReached() {
        breaker.recordFailure("queue-a");
        breaker.recordFailure("queue-a");
        breaker.recordFailure("queue-a");

        assertThat(breaker.isOpen("queue-a")).isTrue();
    }

    @Test
    void isOpen_returnsFalse_afterOpenDurationExpired() {
        breaker.recordFailure("queue-b");
        breaker.recordFailure("queue-b");
        breaker.recordFailure("queue-b");

        Instant afterExpiry = Instant.now().plusMillis(OPEN_DURATION_MS + 1);

        assertThat(breaker.isOpen("queue-b", afterExpiry)).isFalse();
    }

    @Test
    void isOpen_returnsTrue_withinOpenDuration() {
        breaker.recordFailure("queue-c");
        breaker.recordFailure("queue-c");
        breaker.recordFailure("queue-c");

        Instant withinWindow = Instant.now().plusMillis(OPEN_DURATION_MS - 1000);

        assertThat(breaker.isOpen("queue-c", withinWindow)).isTrue();
    }

    @Test
    void recordSuccess_closesCircuit_afterSuccessThreshold() {
        breaker.recordFailure("queue-d");
        breaker.recordFailure("queue-d");
        breaker.recordFailure("queue-d");
        assertThat(breaker.isOpen("queue-d")).isTrue();

        Instant afterExpiry = Instant.now().plusMillis(OPEN_DURATION_MS + 1);
        breaker.isOpen("queue-d", afterExpiry);

        breaker.recordSuccess("queue-d");
        breaker.recordSuccess("queue-d");

        assertThat(breaker.isOpen("queue-d")).isFalse();
    }

    @Test
    void recordSuccess_isNoop_forUnknownQueue() {
        breaker.recordSuccess("queue-unknown");

        assertThat(breaker.isOpen("queue-unknown")).isFalse();
    }

    @Test
    void circuitBreaker_isIndependentPerQueue() {
        breaker.recordFailure("queue-x");
        breaker.recordFailure("queue-x");
        breaker.recordFailure("queue-x");

        assertThat(breaker.isOpen("queue-x")).isTrue();
        assertThat(breaker.isOpen("queue-y")).isFalse();
    }

    @Test
    void recordFailure_resetsHalfOpenSuccessCount() {
        breaker.recordFailure("queue-e");
        breaker.recordFailure("queue-e");
        breaker.recordFailure("queue-e");

        Instant afterExpiry = Instant.now().plusMillis(OPEN_DURATION_MS + 1);
        breaker.isOpen("queue-e", afterExpiry);

        breaker.recordSuccess("queue-e");
        breaker.recordFailure("queue-e");

        assertThat(breaker.isOpen("queue-e")).isFalse();
    }
}
