package com.github.swim_developer.framework.consumer.unit.infrastructure.health;

import com.github.swim_developer.framework.consumer.infrastructure.in.health.reconciliation.ConsumerReconciliationHealthCheck;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class ConsumerReconciliationHealthCheckTest {

    private ReconciliationStateTracker stateTracker;
    private ConsumerReconciliationHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        stateTracker = mock(ReconciliationStateTracker.class);
        healthCheck = new ConsumerReconciliationHealthCheck(stateTracker);
    }

    @Test
    void call_whenReconciled_returnsUpWithReconciledTrue() {
        when(stateTracker.isReconciled()).thenReturn(true);

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo("Subscription Reconciliation");
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsEntry("reconciled", true);
        assertThat(response.getData().get()).containsEntry("consuming_events", true);
    }

    @Test
    void call_whenNotReconciled_returnsUpWithReconciledFalse() {
        when(stateTracker.isReconciled()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsEntry("reconciled", false);
        assertThat(response.getData().get()).containsEntry("consuming_events", false);
    }

    @Test
    void call_whenReconciled_severityIsOk() {
        when(stateTracker.isReconciled()).thenReturn(true);

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getData().get()).containsEntry("severity", "ok");
    }

    @Test
    void call_whenNotReconciled_severityIsWarning() {
        when(stateTracker.isReconciled()).thenReturn(false);

        HealthCheckResponse response = healthCheck.call();

        assertThat(response.getData().get()).containsEntry("severity", "warning");
    }
}
