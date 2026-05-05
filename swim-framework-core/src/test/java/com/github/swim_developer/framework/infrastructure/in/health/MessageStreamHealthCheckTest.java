package com.github.swim_developer.framework.infrastructure.in.health;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MessageStreamHealthCheckTest {

    @Test
    void call_returnsUpWhenBootstrapServersNotConfigured() {
        MessageStreamHealthCheck check = new MessageStreamHealthCheck();
        check.bootstrapServers = Optional.empty();

        var response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).contains("not configured");
    }
}
