package com.github.swim_developer.framework.consumer.infrastructure.in.health.mongo;

import com.github.swim_developer.framework.consumer.application.port.out.SwimPersistenceHealthPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class AbstractMongoDbHealthCheck implements HealthCheck {

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private final SwimPersistenceHealthPort persistencePort;

    protected AbstractMongoDbHealthCheck() {
        this(null);
    }

    @Inject
    public AbstractMongoDbHealthCheck(SwimPersistenceHealthPort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    public HealthCheckResponse call() {
        try {
            long count = persistencePort.count();
            return HealthCheckResponse.named("MongoDB connection")
                    .up()
                    .withData("database", databaseName)
                    .withData("collection", persistencePort.getCollectionName())
                    .withData("documents", count)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("MongoDB connection")
                    .down()
                    .withData("database", databaseName)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
