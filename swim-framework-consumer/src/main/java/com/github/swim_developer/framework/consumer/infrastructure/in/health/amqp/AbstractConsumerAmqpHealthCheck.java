package com.github.swim_developer.framework.consumer.infrastructure.in.health.amqp;

import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AbstractAmqpConsumerManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Slf4j
@Readiness
@ApplicationScoped
public class AbstractConsumerAmqpHealthCheck implements HealthCheck {

    private final ReconciliationStateTracker stateManager;
    private final AbstractAmqpConsumerManager consumerManager;

    @ConfigProperty(name = "swim.service.name", defaultValue = "swim")
    String serviceName;

    protected AbstractConsumerAmqpHealthCheck() {
        this(null, null);
    }

    @Inject
    public AbstractConsumerAmqpHealthCheck(ReconciliationStateTracker stateManager,
                                          AbstractAmqpConsumerManager consumerManager) {
        this.stateManager = stateManager;
        this.consumerManager = consumerManager;
    }

    @Override
    public HealthCheckResponse call() {
        boolean reconciled = stateManager.isReconciled();
        boolean connected = consumerManager.isConnected();
        int activeConsumers = consumerManager.getActiveConsumerCount();

        return HealthCheckResponse.named(serviceName.toUpperCase() + " AMQP Broker Connection")
                .up()
                .withData("connected_providers", consumerManager.getConnectedProviders())
                .withData("connected", connected)
                .withData("active_consumers", activeConsumers)
                .withData("reconciled", reconciled)
                .build();
    }
}
