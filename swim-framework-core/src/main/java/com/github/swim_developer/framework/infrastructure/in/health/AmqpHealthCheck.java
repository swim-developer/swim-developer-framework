package com.github.swim_developer.framework.infrastructure.in.health;

import com.github.swim_developer.framework.application.port.out.AmqpPublisherHealthProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.Optional;

@Readiness
@ApplicationScoped
@Slf4j
public class AmqpHealthCheck implements HealthCheck {

    private final Instance<AmqpPublisherHealthProvider> publisherHealth;
    private final Optional<String> amqpHost;

    @Inject
    public AmqpHealthCheck(
            Instance<AmqpPublisherHealthProvider> publisherHealth,
            @ConfigProperty(name = "amqp-host") Optional<String> amqpHost) {
        this.publisherHealth = publisherHealth;
        this.amqpHost = amqpHost;
    }

    @Override
    public HealthCheckResponse call() {
        if (amqpHost.isEmpty()) {
            return HealthCheckResponse.up("AMQP broker (not configured)");
        }

        if (publisherHealth.isUnsatisfied()) {
            return HealthCheckResponse.named("AMQP broker connection")
                    .down()
                    .withData("error", "No AMQP publisher available")
                    .build();
        }

        boolean healthy = publisherHealth.get().isHealthy();

        return HealthCheckResponse.named("AMQP broker connection")
                .status(healthy)
                .withData("host", amqpHost.get())
                .build();
    }
}
