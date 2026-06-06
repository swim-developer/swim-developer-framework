package com.github.swim_developer.framework.consumer.infrastructure.in.health.reconciliation;

import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Slf4j
@Readiness
@ApplicationScoped
public class ConsumerReconciliationHealthCheck implements HealthCheck {

    private final ReconciliationStateTracker stateManager;

    @Inject
    public ConsumerReconciliationHealthCheck(ReconciliationStateTracker stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public HealthCheckResponse call() {
        boolean reconciled = stateManager.isReconciled();

        return HealthCheckResponse.named("Subscription Reconciliation")
                .status(true)
                .withData("reconciled", reconciled)
                .withData("consuming_events", reconciled)
                .withData("severity", reconciled ? "ok" : "warning")
                .withData("message", reconciled
                        ? "Subscriptions reconciled - receiving events"
                        : "Subscriptions not reconciled - retrying in background")
                .build();
    }
}
