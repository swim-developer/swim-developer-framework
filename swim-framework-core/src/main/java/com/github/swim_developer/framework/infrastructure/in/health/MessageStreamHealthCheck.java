package com.github.swim_developer.framework.infrastructure.in.health;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.Optional;
import java.util.Properties;

@Readiness
@ApplicationScoped
@Slf4j
public class MessageStreamHealthCheck implements HealthCheck {

    private static final int TIMEOUT_MS = 5000;
    private static final String HEALTH_CHECK_NAME = "message-stream";
    private static final String BOOTSTRAP_SERVERS_KEY = "bootstrapServers";

    @ConfigProperty(name = "kafka.BOOTSTRAP_SERVERS_KEY")
    Optional<String> bootstrapServers;

    @Override
    public HealthCheckResponse call() {
        if (bootstrapServers.isEmpty()) {
            return HealthCheckResponse.up("message-stream (not configured)");
        }

        String servers = bootstrapServers.get();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);

        try (AdminClient adminClient = AdminClient.create(props)) {
            ListTopicsOptions options = new ListTopicsOptions().timeoutMs(TIMEOUT_MS);
            int topicCount = adminClient.listTopics(options).names().get().size();

            return HealthCheckResponse.named(HEALTH_CHECK_NAME)
                    .up()
                    .withData(BOOTSTRAP_SERVERS_KEY, servers)
                    .withData("topics", topicCount)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Message stream health check interrupted");
            return HealthCheckResponse.named(HEALTH_CHECK_NAME)
                    .down()
                    .withData(BOOTSTRAP_SERVERS_KEY, servers)
                    .withData("error", "Health check interrupted")
                    .build();
        } catch (Exception e) {
            log.error("Message stream health check failed: {}", e.getMessage());
            return HealthCheckResponse.named(HEALTH_CHECK_NAME)
                    .down()
                    .withData(BOOTSTRAP_SERVERS_KEY, servers)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
