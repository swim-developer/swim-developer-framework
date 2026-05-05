package com.github.swim_developer.framework.consumer.application.heartbeat.timeout;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.out.client.SwimSubscriptionManagerClientPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import com.github.swim_developer.framework.domain.model.HeartbeatTimeoutEvent;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(TestNameLoggerExtension.class)
class AbstractHeartbeatTimeoutHandlerTest {

    private SubscriptionHeartbeatTracker tracker;
    private Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private Instance<SwimSubscriptionManagerClientPort> smRegistries;
    private AbstractSubscriptionService service;
    private SwimSubscriptionManagerClientPort smRegistry;

    private AbstractHeartbeatTimeoutHandler handler;
    private SwimConsumerSubscription mockSubscription;

    private static final SubscriptionHeartbeat DUMMY_HEARTBEAT =
            new SubscriptionHeartbeat(UUID.randomUUID(), "ACTIVE", "OK", Instant.now(), Instant.now(), 1L);

    @BeforeEach
    void setUp() {
        tracker = mock(SubscriptionHeartbeatTracker.class);
        subscriptionServices = mock(Instance.class);
        smRegistries = mock(Instance.class);
        service = mock(AbstractSubscriptionService.class);
        smRegistry = mock(SwimSubscriptionManagerClientPort.class);

        when(subscriptionServices.get()).thenReturn((SwimSubscriptionLifecyclePort) service);
        when(smRegistries.get()).thenReturn(smRegistry);

        mockSubscription = new SwimConsumerSubscription() {
            @Override public String getSubscriptionId() { return "sub-1"; }
            @Override public String getQueueName() { return "queue-1"; }
            @Override public String getProviderId() { return "provider-1"; }
        };

        handler = new AbstractHeartbeatTimeoutHandler(tracker, subscriptionServices, smRegistries);
    }

    private HeartbeatTimeoutEvent event(String subscriptionId) {
        return new HeartbeatTimeoutEvent(subscriptionId, DUMMY_HEARTBEAT);
    }

    private ProviderConfiguration providerConfig() {
        return new ProviderConfiguration("provider-1", null, null);
    }

    @Test
    void onHeartbeatTimeout_removesFromTrackerWhenSubscriptionNotFoundLocally() {
        when(service.findBySubscriptionId("sub-unknown")).thenReturn(Optional.empty());

        handler.onHeartbeatTimeout(event("sub-unknown"));

        verify(tracker).remove("sub-unknown");
        verify(service, never()).handleSubscriptionLost(any(), any());
    }

    @Test
    void onHeartbeatTimeout_triggersRecoveryForTerminatedState() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any())).thenReturn("TERMINATED");

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service).handleSubscriptionLost(eq("sub-1"), eq(mockSubscription));
    }

    @Test
    void onHeartbeatTimeout_triggersRecoveryForDeletedState() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any())).thenReturn("DELETED");

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service).handleSubscriptionLost(eq("sub-1"), eq(mockSubscription));
    }

    @Test
    void onHeartbeatTimeout_doesNotTriggerRecoveryForActiveState() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any())).thenReturn("ACTIVE");

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service, never()).handleSubscriptionLost(any(), any());
    }

    @Test
    void onHeartbeatTimeout_triggersRecoveryOn404FromRemote() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any()))
                .thenThrow(new WebApplicationException(Response.Status.NOT_FOUND));

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service).handleSubscriptionLost(eq("sub-1"), eq(mockSubscription));
    }

    @Test
    void onHeartbeatTimeout_triggersRecoveryOn410FromRemote() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any()))
                .thenThrow(new WebApplicationException(Response.Status.GONE));

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service).handleSubscriptionLost(eq("sub-1"), eq(mockSubscription));
    }

    @Test
    void onHeartbeatTimeout_doesNotTriggerRecoveryOnOtherHttpError() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any()))
                .thenThrow(new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR));

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service, never()).handleSubscriptionLost(any(), any());
    }

    @Test
    void onHeartbeatTimeout_doesNotTriggerRecoveryOnGenericException() {
        when(service.findBySubscriptionId("sub-1")).thenReturn(Optional.of(mockSubscription));
        when(service.resolveProvider("provider-1")).thenReturn(providerConfig());
        when(smRegistry.querySubscriptionStatus(eq("sub-1"), any()))
                .thenThrow(new RuntimeException("network error"));

        handler.onHeartbeatTimeout(event("sub-1"));

        verify(service, never()).handleSubscriptionLost(any(), any());
    }
}
