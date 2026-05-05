package com.github.swim_developer.framework.infrastructure.in.health;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import java.sql.Connection;
import java.sql.Statement;

@Liveness
@ApplicationScoped
@IfBuildProperty(name = "swim.health.jdbc.enabled", stringValue = "true", enableIfMissing = false)
@Slf4j
public class DatabaseLivenessCheck implements HealthCheck {

    private final Instance<AgroalDataSource> dataSourceInstance;

    @Inject
    public DatabaseLivenessCheck(Instance<AgroalDataSource> dataSourceInstance) {
        this.dataSourceInstance = dataSourceInstance;
    }

    @Override
    public HealthCheckResponse call() {
        if (!dataSourceInstance.isResolvable()) {
            return HealthCheckResponse.up("Database (not configured)");
        }
        try (Connection connection = dataSourceInstance.get().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return HealthCheckResponse.up("Database");
        } catch (Exception e) {
            log.error("Database liveness check failed: {}", e.getMessage());
            return HealthCheckResponse.down("Database");
        }
    }
}
