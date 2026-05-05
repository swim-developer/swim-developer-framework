package com.github.swim_developer.framework.infrastructure.in.health;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;
import java.sql.Statement;

@Readiness
@ApplicationScoped
@IfBuildProperty(name = "swim.health.jdbc.enabled", stringValue = "true", enableIfMissing = false)
@Slf4j
public class DatabaseReadinessCheck implements HealthCheck {

    private static final String VALIDATION_QUERY = "SELECT 1";

    private final Instance<AgroalDataSource> dataSourceInstance;

    @Inject
    public DatabaseReadinessCheck(Instance<AgroalDataSource> dataSourceInstance) {
        this.dataSourceInstance = dataSourceInstance;
    }

    @Override
    public HealthCheckResponse call() {
        if (!dataSourceInstance.isResolvable()) {
            return HealthCheckResponse.up("Database readiness (not configured)");
        }
        try (Connection connection = dataSourceInstance.get().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(VALIDATION_QUERY);
            return HealthCheckResponse.named("Database readiness")
                    .up()
                    .build();
        } catch (Exception e) {
            log.error("Database readiness check failed: {}", e.getMessage());
            return HealthCheckResponse.named("Database readiness")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
