package com.github.swim_developer.framework.consumer.application.subscription.schedule;

import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionCountPort;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AbstractAmqpConsumerManager;
import com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription.SwimSubscriptionConfigParserPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.filter.SubscriptionFilterCache;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(TestNameLoggerExtension.class)
class AbstractReconciliationSchedulerTest {

    private LeaderElection leaderElection;
    private ReconciliationStateTracker stateTracker;
    private SubscriptionFilterCache filterCache;
    private SwimIdempotencyPort idempotencyPort;
    private AbstractAmqpConsumerManager consumerManager;
    private Instance<SwimSubscriptionConfigParserPort> configParsers;
    private Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private Instance<SwimSubscriptionCountPort> repositories;
    private SwimSubscriptionConfigParserPort parser;
    private AbstractSubscriptionService service;
    private SwimSubscriptionCountPort repository;

    private AbstractReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        leaderElection = mock(LeaderElection.class);
        stateTracker = mock(ReconciliationStateTracker.class);
        filterCache = mock(SubscriptionFilterCache.class);
        idempotencyPort = mock(SwimIdempotencyPort.class);
        consumerManager = mock(AbstractAmqpConsumerManager.class);
        configParsers = mock(Instance.class);
        subscriptionServices = mock(Instance.class);
        repositories = mock(Instance.class);
        parser = mock(SwimSubscriptionConfigParserPort.class);
        service = mock(AbstractSubscriptionService.class);
        repository = mock(SwimSubscriptionCountPort.class);

        when(configParsers.get()).thenReturn(parser);
        when(subscriptionServices.get()).thenReturn(service);
        when(repositories.get()).thenReturn(repository);

        scheduler = new AbstractReconciliationScheduler(
                leaderElection, stateTracker, filterCache, idempotencyPort,
                consumerManager, 3, false,
                configParsers, subscriptionServices, repositories);
    }

    @Test
    void retryReconciliation_doesNothing_whenNotLeader() {
        when(leaderElection.isLeader()).thenReturn(false);

        scheduler.retryReconciliation();

        verify(stateTracker, never()).isReconciled();
    }

    @Test
    void retryReconciliation_callsEnsureDesiredSubscriptions_whenAlreadyReconciled() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(true);
        when(consumerManager.getActiveConsumerCount()).thenReturn(2);
        when(repository.countTotalSubscriptions()).thenReturn(2L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1"));
        when(service.reconcileCreate(any())).thenReturn(new ReconciliationResult(1, 1, 0, 0));

        scheduler.retryReconciliation();

        verify(parser).parseDesiredSubscriptions();
    }

    @Test
    void retryReconciliation_marksReconciledAndRegistersConsumers_whenFullyReconciled() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(false).thenReturn(true);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1", "sub-2"));
        when(service.reconcileCreate(any())).thenReturn(new ReconciliationResult(2, 2, 0, 0));

        scheduler.retryReconciliation();

        verify(stateTracker).markAsReconciled();
        verify(idempotencyPort).warmup();
        verify(service).registerAllActiveConsumers();
    }

    @Test
    void retryReconciliation_doesNotMarkReconciled_whenPartiallyReconciled() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(false);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1", "sub-2"));
        when(service.reconcileCreate(any())).thenReturn(new ReconciliationResult(2, 1, 1, 0));

        scheduler.retryReconciliation();

        verify(stateTracker, never()).markAsReconciled();
    }

    @Test
    void retryReconciliation_handlesException_gracefully() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(false);
        when(parser.parseDesiredSubscriptions()).thenThrow(new RuntimeException("reconciliation error"));

        scheduler.retryReconciliation();

        verify(stateTracker, never()).markAsReconciled();
    }

    @Test
    void checkAndReconnect_refreshesFilterCache_atConfiguredInterval() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(true);
        when(consumerManager.getActiveConsumerCount()).thenReturn(2);
        when(repository.countTotalSubscriptions()).thenReturn(2L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of());

        scheduler.retryReconciliation();
        scheduler.retryReconciliation();
        scheduler.retryReconciliation();

        verify(filterCache).refreshAll();
    }

    @Test
    void checkAndReconnect_reconnectsConsumers_whenConsumerCountLow() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(true);
        when(consumerManager.getActiveConsumerCount()).thenReturn(1);
        when(repository.countTotalSubscriptions()).thenReturn(3L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of());

        scheduler.retryReconciliation();

        verify(consumerManager).resetClient();
        verify(service).registerAllActiveConsumers();
    }

    @Test
    void checkAndReconnect_reconnectsConsumers_whenZombiesDetected() {
        when(leaderElection.isLeader()).thenReturn(true);
        when(stateTracker.isReconciled()).thenReturn(true);
        when(consumerManager.getActiveConsumerCount()).thenReturn(2);
        when(consumerManager.hasZombieConsumers()).thenReturn(true);
        when(repository.countTotalSubscriptions()).thenReturn(2L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of());

        scheduler.retryReconciliation();

        verify(consumerManager).resetClient();
        verify(service).registerAllActiveConsumers();
    }
}
