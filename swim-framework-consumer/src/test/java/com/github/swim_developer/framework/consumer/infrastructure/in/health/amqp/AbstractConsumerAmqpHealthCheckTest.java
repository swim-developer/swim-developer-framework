package com.github.swim_developer.framework.consumer.infrastructure.in.health.amqp;

import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AbstractAmqpConsumerManager;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractConsumerAmqpHealthCheckTest {

    private AbstractConsumerAmqpHealthCheck check(boolean connected, boolean reconciled) {
        ReconciliationStateTracker stateTracker = mock(ReconciliationStateTracker.class);
        AbstractAmqpConsumerManager consumerManager = mock(AbstractAmqpConsumerManager.class);
        when(stateTracker.isReconciled()).thenReturn(reconciled);
        when(consumerManager.isConnected()).thenReturn(connected);
        when(consumerManager.getActiveConsumerCount()).thenReturn(connected ? 2 : 0);
        when(consumerManager.getConnectedProviders()).thenReturn(connected ? "provider-1" : "");
        AbstractConsumerAmqpHealthCheck c = new AbstractConsumerAmqpHealthCheck(stateTracker, consumerManager);
        c.serviceName = "dnotam";
        return c;
    }

    @Test
    void call_returnsUpWithAllData_whenConnected() {
        HealthCheckResponse response = check(true, true).call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get())
                .containsEntry("connected", true)
                .containsEntry("reconciled", true)
                .containsEntry("active_consumers", 2L)
                .containsKey("connected_providers");
    }

    @Test
    void call_returnsUpWithConnectedFalse_whenDisconnected() {
        HealthCheckResponse response = check(false, false).call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData().get())
                .containsEntry("connected", false)
                .containsEntry("reconciled", false);
    }
}
