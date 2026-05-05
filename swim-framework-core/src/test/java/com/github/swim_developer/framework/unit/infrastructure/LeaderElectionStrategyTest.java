package com.github.swim_developer.framework.unit.infrastructure;

import com.github.swim_developer.framework.application.port.out.LeaderElectionStrategy;
import com.github.swim_developer.framework.infrastructure.out.cluster.StandaloneLeaderElectionStrategy;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.testing.SwimRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TestNameLoggerExtension.class)
@SwimRequirement(
    spec = "EUROCONTROL SPEC-170",
    section = "5.3 High Availability",
    description = "Provider services MUST implement leader election for singleton operations"
)
class LeaderElectionStrategyTest {

    private LeaderElectionStrategy standaloneStrategy;

    @BeforeEach
    void setUp() {
        standaloneStrategy = new StandaloneLeaderElectionStrategy("test-pod-0");
    }

    @Test
    @SwimRequirement(
        spec = "EUROCONTROL SPEC-170",
        section = "5.3.1 Standalone Deployment",
        description = "Single instance deployments MUST always report as leader"
    )
    void standaloneStrategyAlwaysReturnsLeader() {
        standaloneStrategy.start();

        assertThat(standaloneStrategy.isLeader()).isTrue();
        assertThat(standaloneStrategy.getIdentity()).isEqualTo("test-pod-0");

        standaloneStrategy.stop();
    }

    @Test
    @SwimRequirement(
        spec = "EUROCONTROL SPEC-170",
        section = "5.3.3 Instance Identity",
        description = "Each instance MUST have a unique identity for cluster coordination"
    )
    void strategyProvidesUniqueIdentity() {
        LeaderElectionStrategy instance1 = new StandaloneLeaderElectionStrategy("pod-1");
        LeaderElectionStrategy instance2 = new StandaloneLeaderElectionStrategy("pod-2");

        instance1.start();
        instance2.start();

        assertThat(instance1.getIdentity()).isEqualTo("pod-1");
        assertThat(instance2.getIdentity()).isEqualTo("pod-2");
        assertThat(instance1.getIdentity()).isNotEqualTo(instance2.getIdentity());
    }

}
