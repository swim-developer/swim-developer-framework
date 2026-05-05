package com.github.swim_developer.framework.provider.unit.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.swim_developer.framework.application.port.out.SubscriptionExpiryStrategy;
import com.github.swim_developer.framework.domain.model.SubscriptionExpiry;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.application.subscription.SubscriptionExpiryScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("application")
@SuppressWarnings("unchecked")
@ExtendWith(TestNameLoggerExtension.class)
class SubscriptionExpirySchedulerTest {

    private SubscriptionExpiryScheduler scheduler;
    private SubscriptionExpiryStrategy strategy;
    private Instance<SubscriptionExpiryStrategy> strategyInstance;
    private SimpleMeterRegistry meterRegistry;
    private LeaderElection leaderElection;

    @BeforeEach
    void setUp() {
        strategy = mock(SubscriptionExpiryStrategy.class);
        strategyInstance = mock(Instance.class);
        meterRegistry = new SimpleMeterRegistry();
        leaderElection = mock(LeaderElection.class);
        when(leaderElection.isLeader()).thenReturn(true);

        scheduler = new SubscriptionExpiryScheduler(
            Duration.ofHours(24),
            strategyInstance,
            meterRegistry,
            leaderElection
        );
    }

    @Test
    void checkExpiredDoesNothingWhenNoStrategy() {
        when(strategyInstance.isResolvable()).thenReturn(false);

        scheduler.checkExpiredSubscriptions();

        verify(strategyInstance, never()).get();
    }

    @Test
    void checkExpiredSkipsWhenNoneFound() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findExpiredSubscriptions(any())).thenReturn(Collections.emptyList());

        scheduler.checkExpiredSubscriptions();

        verify(strategy, never()).terminateSubscription(any());
    }

    @Test
    void checkExpiredTerminatesAllExpired() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findExpiredSubscriptions(any())).thenReturn(List.of(
                new SubscriptionExpiry("sub-1", Instant.now().minusSeconds(3600), "ACTIVE"),
                new SubscriptionExpiry("sub-2", Instant.now().minusSeconds(7200), "ACTIVE")));

        scheduler.checkExpiredSubscriptions();

        verify(strategy).terminateSubscription("sub-1");
        verify(strategy).terminateSubscription("sub-2");
    }

    @Test
    void checkExpiredContinuesOnPartialFailure() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findExpiredSubscriptions(any())).thenReturn(List.of(
                new SubscriptionExpiry("sub-fail", Instant.now(), "ACTIVE"),
                new SubscriptionExpiry("sub-ok", Instant.now(), "ACTIVE")));
        doThrow(new RuntimeException("DB error")).when(strategy).terminateSubscription("sub-fail");

        scheduler.checkExpiredSubscriptions();

        verify(strategy).terminateSubscription("sub-fail");
        verify(strategy).terminateSubscription("sub-ok");
    }

    @Test
    void purgeDoesNothingWhenNoStrategy() {
        when(strategyInstance.isResolvable()).thenReturn(false);

        scheduler.purgeTerminatedSubscriptions();

        verify(strategyInstance, never()).get();
    }

    @Test
    void purgeSkipsWhenNoneFound() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findTerminatedSubscriptionsToPurge(any())).thenReturn(Collections.emptyList());

        scheduler.purgeTerminatedSubscriptions();

        verify(strategy, never()).purgeSubscription(any());
    }

    @Test
    void purgePurgesAllEligible() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findTerminatedSubscriptionsToPurge(any())).thenReturn(List.of(
                new SubscriptionExpiry("sub-old", Instant.now().minus(Duration.ofDays(2)), "TERMINATED")));

        scheduler.purgeTerminatedSubscriptions();

        verify(strategy).purgeSubscription("sub-old");
    }

    @Test
    void purgeContinuesOnPartialFailure() {
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
        when(strategy.findTerminatedSubscriptionsToPurge(any())).thenReturn(List.of(
                new SubscriptionExpiry("sub-fail", Instant.now(), "TERMINATED"),
                new SubscriptionExpiry("sub-ok", Instant.now(), "TERMINATED")));
        doThrow(new RuntimeException("fail")).when(strategy).purgeSubscription("sub-fail");

        scheduler.purgeTerminatedSubscriptions();

        verify(strategy).purgeSubscription("sub-fail");
        verify(strategy).purgeSubscription("sub-ok");
    }
}
