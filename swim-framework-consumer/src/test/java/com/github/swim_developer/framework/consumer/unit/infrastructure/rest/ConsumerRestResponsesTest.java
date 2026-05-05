package com.github.swim_developer.framework.consumer.unit.infrastructure.rest;

import com.github.swim_developer.framework.consumer.infrastructure.in.rest.ConsumerRestResponses;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class ConsumerRestResponsesTest {

    @Test
    void ok_returns200WithEntity() {
        Response response = ConsumerRestResponses.ok(Map.of("key", "value"));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void created_returns201WithEntity() {
        Response response = ConsumerRestResponses.created(Map.of("id", "sub-1"));
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getEntity()).isEqualTo(Map.of("id", "sub-1"));
    }

    @Test
    void noContent_returns204() {
        Response response = ConsumerRestResponses.noContent();
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void notFound_returns404WithErrorBody() {
        Response response = ConsumerRestResponses.notFound("Subscription not found: sub-99");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getEntity()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry(ConsumerRestResponses.ERROR_KEY, "Subscription not found: sub-99");
    }

    @Test
    void badRequest_returns400WithErrorBody() {
        Response response = ConsumerRestResponses.badRequest("Invalid status value");
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry(ConsumerRestResponses.ERROR_KEY, "Invalid status value");
    }

    @Test
    void serviceUnavailable_returns503WithErrorBody() {
        Response response = ConsumerRestResponses.serviceUnavailable("Provider unreachable");
        assertThat(response.getStatus()).isEqualTo(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry(ConsumerRestResponses.ERROR_KEY, "Provider unreachable");
    }

    @Test
    void isValidSubscriptionStatus_returnsTrueForActive() {
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("ACTIVE")).isTrue();
    }

    @Test
    void isValidSubscriptionStatus_returnsTrueForPaused() {
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("PAUSED")).isTrue();
    }

    @Test
    void isValidSubscriptionStatus_returnsTrueForDeleted() {
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("DELETED")).isTrue();
    }

    @Test
    void isValidSubscriptionStatus_returnsFalseForInvalidStatus() {
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("INVALID")).isFalse();
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("active")).isFalse();
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus("")).isFalse();
        assertThat(ConsumerRestResponses.isValidSubscriptionStatus(null)).isFalse();
    }

    @Test
    void parseDateOrThrow_parsesValidIso8601() {
        Instant result = ConsumerRestResponses.parseDateOrThrow("2025-12-01T00:00:00Z");
        assertThat(result).isEqualTo(Instant.parse("2025-12-01T00:00:00Z"));
    }

    @Test
    void parseDateOrThrow_throwsForInvalidFormat() {
        assertThatThrownBy(() -> ConsumerRestResponses.parseDateOrThrow("01/12/2025"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void parseDateOrThrow_throwsForNull() {
        assertThatThrownBy(() -> ConsumerRestResponses.parseDateOrThrow(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidDateRange_returnsTrueWhenStartBeforeEnd() {
        Instant start = Instant.parse("2025-12-01T00:00:00Z");
        Instant end = Instant.parse("2025-12-31T23:59:59Z");
        assertThat(ConsumerRestResponses.isValidDateRange(start, end)).isTrue();
    }

    @Test
    void isValidDateRange_returnsTrueWhenStartEqualsEnd() {
        Instant instant = Instant.parse("2025-12-01T00:00:00Z");
        assertThat(ConsumerRestResponses.isValidDateRange(instant, instant)).isTrue();
    }

    @Test
    void isValidDateRange_returnsFalseWhenStartAfterEnd() {
        Instant start = Instant.parse("2025-12-31T00:00:00Z");
        Instant end = Instant.parse("2025-12-01T00:00:00Z");
        assertThat(ConsumerRestResponses.isValidDateRange(start, end)).isFalse();
    }

    @Test
    void subscriptionNotFoundPrefix_isCorrect() {
        assertThat(ConsumerRestResponses.SUBSCRIPTION_NOT_FOUND_PREFIX)
                .isEqualTo("Subscription not found: ");
    }
}
