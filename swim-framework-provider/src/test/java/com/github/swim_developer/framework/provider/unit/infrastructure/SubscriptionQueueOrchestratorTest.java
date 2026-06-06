package com.github.swim_developer.framework.provider.unit.infrastructure;

import com.github.swim_developer.framework.application.port.out.QueueProvisioningStrategy;
import com.github.swim_developer.framework.domain.exception.QueueProvisioningException;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.infrastructure.out.queue.SubscriptionQueueOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class SubscriptionQueueOrchestratorTest {

    private QueueProvisioningStrategy provisioner;
    private SubscriptionQueueOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        provisioner = mock(QueueProvisioningStrategy.class);
        orchestrator = new SubscriptionQueueOrchestrator(provisioner, "-amq");
    }

    @Test
    void provision_createsQueueAndAddsSecurityRole() {
        orchestrator.provision("queue-1", "user1");

        verify(provisioner).createQueue("queue-1");
        verify(provisioner).addSecurityRole("queue-1", "user1", "-amq");
    }

    @Test
    void provision_throwsQueueProvisioningException_whenCreateQueueFails() {
        doThrow(new RuntimeException("create error"))
                .when(provisioner).createQueue(any());

        assertThatThrownBy(() -> orchestrator.provision("queue-1", "user1"))
                .isInstanceOf(QueueProvisioningException.class)
                .hasMessageContaining("configure queue");

        verify(provisioner, never()).addSecurityRole(any(), any(), any());
    }

    @Test
    void provision_rollsBackQueue_whenSecurityRoleFails() {
        doThrow(new RuntimeException("role error"))
                .when(provisioner).addSecurityRole(any(), any(), any());

        assertThatThrownBy(() -> orchestrator.provision("queue-1", "user1"))
                .isInstanceOf(QueueProvisioningException.class)
                .hasMessageContaining("security role");

        verify(provisioner).removeQueue("queue-1");
    }

    @Test
    void deprovision_removesQueueAndRole() {
        orchestrator.deprovision("queue-2");

        verify(provisioner).removeQueue("queue-2");
        verify(provisioner).removeSecurityRole("queue-2");
    }

    @Test
    void deprovision_continuesWhenRemoveQueueFails() {
        doThrow(new RuntimeException("remove error"))
                .when(provisioner).removeQueue(any());

        orchestrator.deprovision("queue-2");

        verify(provisioner).removeSecurityRole("queue-2");
    }

    @Test
    void deprovision_continuesWhenRemoveRoleFails() {
        doThrow(new RuntimeException("role error"))
                .when(provisioner).removeSecurityRole(any());

        orchestrator.deprovision("queue-2");

        verify(provisioner).removeQueue("queue-2");
    }
}
