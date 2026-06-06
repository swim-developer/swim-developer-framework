package com.github.swim_developer.framework.integration.messaging;

import com.github.swim_developer.framework.infrastructure.in.health.MessageStreamHealthCheck;
import com.github.swim_developer.framework.integration.containers.RedpandaTestContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(MessageStreamHealthCheckIT.KafkaProfile.class)
@Tag("integration")
@Tag("messaging")
class MessageStreamHealthCheckIT {

    @Inject
    @Readiness
    MessageStreamHealthCheck healthCheck;

    @Test
    void healthCheck_returnsUp_whenKafkaIsReachable() {
        HealthCheckResponse response = healthCheck.call();

        assertThat(response).extracting(HealthCheckResponse::getStatus, HealthCheckResponse::getName)
                .containsExactly(HealthCheckResponse.Status.UP, "message-stream");
        assertThat(response.getData().orElse(null)).containsKeys("bootstrapServers", "topics");
    }

    @Test
    void healthCheck_includesTopicCount() {
        HealthCheckResponse response = healthCheck.call();

        Object topicCount = response.getData().orElse(null).get("topics");
        assertThat(topicCount).isNotNull().isInstanceOfAny(Integer.class, Long.class);
        assertThat(((Number) topicCount).intValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void healthCheck_includesBootstrapServers() {
        HealthCheckResponse response = healthCheck.call();

        Object bootstrapServers = response.getData().orElse(null).get("bootstrapServers");
        assertThat(bootstrapServers).isNotNull();
        assertThat(bootstrapServers.toString()).contains("localhost");
    }

    @Test
    void healthCheck_canBeCalledMultipleTimes() {
        HealthCheckResponse response1 = healthCheck.call();
        HealthCheckResponse response2 = healthCheck.call();

        assertThat(response1.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response2.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    public static class KafkaProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return RedpandaTestContainer.getQuarkusConfig();
        }
    }
}
