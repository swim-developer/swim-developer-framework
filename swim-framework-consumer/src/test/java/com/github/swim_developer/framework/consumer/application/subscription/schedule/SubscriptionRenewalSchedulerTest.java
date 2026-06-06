package com.github.swim_developer.framework.consumer.application.subscription.schedule;

import com.github.swim_developer.framework.application.port.out.SubscriptionRenewalStrategy;
import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import com.github.swim_developer.framework.domain.exception.SubscriptionRenewalException;
import com.github.swim_developer.framework.domain.model.SubscriptionRenewalInfo;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(TestNameLoggerExtension.class)
class SubscriptionRenewalSchedulerTest {

    private SubscriptionRenewalStrategy strategy;
    private LeaderElection leaderElection;
    @SuppressWarnings("unchecked")
    private Instance<SubscriptionRenewalStrategy> strategyInstance = mock(Instance.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        strategy = mock(SubscriptionRenewalStrategy.class);
        leaderElection = mock(LeaderElection.class);
        strategyInstance = mock(Instance.class);
        when(strategyInstance.isResolvable()).thenReturn(true);
        when(strategyInstance.get()).thenReturn(strategy);
    }

    @Test
    void checkAndRenewSubscriptions_doesNothing_whenRenewalDisabled() {
        SubscriptionRenewalScheduler scheduler = scheduler(false);
        when(leaderElection.isLeader()).thenReturn(true);

        scheduler.checkAndRenewSubscriptions();

        verify(strategy, never()).findSubscriptionsNearExpiry(any());
    }

    @Test
    void checkAndRenewSubscriptions_doesNothing_whenNotLeader() {
        SubscriptionRenewalScheduler scheduler = scheduler(true);
        when(leaderElection.isLeader()).thenReturn(false);

        scheduler.checkAndRenewSubscriptions();

        verify(strategy, never()).findSubscriptionsNearExpiry(any());
    }

    @Test
    void checkAndRenewSubscriptions_doesNothing_whenNoSubscriptionsNearExpiry() {
        SubscriptionRenewalScheduler scheduler = scheduler(true);
        when(leaderElection.isLeader()).thenReturn(true);
        when(strategy.findSubscriptionsNearExpiry(any())).thenReturn(List.of());

        scheduler.checkAndRenewSubscriptions();

        verify(strategy, never()).renewSubscription(any());
    }

    @Test
    void checkAndRenewSubscriptions_renewsSubscription_onHappyPath() throws Exception {
        SubscriptionRenewalScheduler scheduler = scheduler(true);
        when(leaderElection.isLeader()).thenReturn(true);
        when(strategy.findSubscriptionsNearExpiry(any())).thenReturn(
                List.of(new SubscriptionRenewalInfo("sub-1", Instant.now().plusSeconds(1800))));

        scheduler.checkAndRenewSubscriptions();

        verify(strategy).renewSubscription("sub-1");
    }

    @Test
    void checkAndRenewSubscriptions_delegatesToOnSubscriptionLost_on404() throws Exception {
        SubscriptionRenewalScheduler scheduler = scheduler(true);
        when(leaderElection.isLeader()).thenReturn(true);
        when(strategy.findSubscriptionsNearExpiry(any())).thenReturn(
                List.of(new SubscriptionRenewalInfo("sub-2", Instant.now().plusSeconds(1800))));
        doThrow(new SubscriptionNotFoundException("sub-2"))
                .when(strategy).renewSubscription("sub-2");

        scheduler.checkAndRenewSubscriptions();

        verify(strategy).onSubscriptionLost("sub-2");
    }

    @Test
    void checkAndRenewSubscriptions_incrementsFailureCounter_onRenewalException() throws Exception {
        SubscriptionRenewalScheduler scheduler = scheduler(true);
        when(leaderElection.isLeader()).thenReturn(true);
        when(strategy.findSubscriptionsNearExpiry(any())).thenReturn(
                List.of(new SubscriptionRenewalInfo("sub-3", Instant.now().plusSeconds(1800))));
        doThrow(new SubscriptionRenewalException("sub-3", new RuntimeException("timeout")))
                .when(strategy).renewSubscription("sub-3");

        scheduler.checkAndRenewSubscriptions();

        verify(strategy).renewSubscription("sub-3");
    }

    @Test
    void calculateBackoffDelay_doublesWithEachAttempt() {
        assertThat(SubscriptionRenewalScheduler.calculateBackoffDelay(1)).isEqualTo(1000L);
        assertThat(SubscriptionRenewalScheduler.calculateBackoffDelay(2)).isEqualTo(2000L);
        assertThat(SubscriptionRenewalScheduler.calculateBackoffDelay(3)).isEqualTo(4000L);
    }

    @Test
    void calculateBackoffDelay_capsAt30Seconds() {
        assertThat(SubscriptionRenewalScheduler.calculateBackoffDelay(10)).isEqualTo(30000L);
    }

    private SubscriptionRenewalScheduler scheduler(boolean renewalEnabled) {
        return new SubscriptionRenewalScheduler(
                renewalEnabled,
                Duration.ofHours(1),
                1,
                strategyInstance,
                new SimpleMeterRegistry(),
                leaderElection);
    }
}
