package com.github.swim_developer.framework.provider.application.heartbeat;

import com.github.swim_developer.framework.application.port.out.ActiveSubscriptionSupplier;
import com.github.swim_developer.framework.application.port.out.SubscriptionHeartbeatPublisher;
import com.github.swim_developer.framework.domain.model.ActiveSubscriptionInfo;
import java.util.UUID;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.mockito.Mockito.*;

@Tag("unit")
@SuppressWarnings("unchecked")
@ExtendWith(TestNameLoggerExtension.class)
class PerSubscriptionHeartbeatSchedulerTest {

    private Instance<SubscriptionHeartbeatPublisher> publisherInstance;
    private Instance<ActiveSubscriptionSupplier> supplierInstance;
    private SubscriptionHeartbeatPublisher publisher;
    private ActiveSubscriptionSupplier supplier;
    private LeaderElection leaderElection;
    private SimpleMeterRegistry registry;

    private PerSubscriptionHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() {
        publisher = mock(SubscriptionHeartbeatPublisher.class);
        supplier = mock(ActiveSubscriptionSupplier.class);
        publisherInstance = mock(Instance.class);
        supplierInstance = mock(Instance.class);
        leaderElection = mock(LeaderElection.class);
        registry = new SimpleMeterRegistry();

        when(publisherInstance.isResolvable()).thenReturn(true);
        when(publisherInstance.get()).thenReturn(publisher);
        when(supplierInstance.isResolvable()).thenReturn(true);
        when(supplierInstance.get()).thenReturn(supplier);
        when(leaderElection.isLeader()).thenReturn(true);

        scheduler = new PerSubscriptionHeartbeatScheduler(
                true, "15s", "test-provider",
                publisherInstance, supplierInstance, registry, leaderElection);
        scheduler.initMetrics();
    }

    @Test
    void sendHeartbeats_skips_whenHeartbeatDisabled() {
        scheduler = new PerSubscriptionHeartbeatScheduler(
                false, "15s", "test-provider",
                publisherInstance, supplierInstance, registry, leaderElection);
        scheduler.initMetrics();

        scheduler.sendHeartbeats();

        verifyNoInteractions(publisher);
    }

    @Test
    void sendHeartbeats_skips_whenPublisherNotResolvable() {
        when(publisherInstance.isResolvable()).thenReturn(false);

        scheduler.sendHeartbeats();

        verifyNoInteractions(publisher);
    }

    @Test
    void sendHeartbeats_skips_whenSupplierNotResolvable() {
        when(supplierInstance.isResolvable()).thenReturn(false);

        scheduler.sendHeartbeats();

        verifyNoInteractions(publisher);
    }

    @Test
    void sendHeartbeats_skips_whenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);

        scheduler.sendHeartbeats();

        verifyNoInteractions(publisher);
    }

    @Test
    void sendHeartbeats_skips_whenNoActiveSubscriptions() {
        when(supplier.getActiveSubscriptions()).thenReturn(List.of());

        scheduler.sendHeartbeats();

        verifyNoInteractions(publisher);
    }

    @Test
    void sendHeartbeats_publishesHeartbeatForEachSubscription() {
        ActiveSubscriptionInfo sub1 = new ActiveSubscriptionInfo(UUID.randomUUID(), "queue-1", "ACTIVE");
        ActiveSubscriptionInfo sub2 = new ActiveSubscriptionInfo(UUID.randomUUID(), "queue-2", "ACTIVE");
        when(supplier.getActiveSubscriptions()).thenReturn(List.of(sub1, sub2));

        scheduler.sendHeartbeats();

        verify(publisher).publishHeartbeat(eq("queue-1"), any());
        verify(publisher).publishHeartbeat(eq("queue-2"), any());
    }

    @Test
    void sendHeartbeats_continuesOnPublishFailure() {
        ActiveSubscriptionInfo sub1 = new ActiveSubscriptionInfo(UUID.randomUUID(), "queue-1", "ACTIVE");
        ActiveSubscriptionInfo sub2 = new ActiveSubscriptionInfo(UUID.randomUUID(), "queue-2", "ACTIVE");
        when(supplier.getActiveSubscriptions()).thenReturn(List.of(sub1, sub2));
        doThrow(new RuntimeException("publish error")).when(publisher).publishHeartbeat(eq("queue-1"), any());

        scheduler.sendHeartbeats();

        verify(publisher).publishHeartbeat(eq("queue-2"), any());
    }
}
