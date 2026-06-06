package com.github.swim_developer.framework.consumer.application.subscription.startup;

import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionCountPort;
import com.github.swim_developer.framework.consumer.application.subscription.service.AbstractSubscriptionService;
import com.github.swim_developer.framework.consumer.application.subscription.service.SwimSubscriptionLifecyclePort;
import com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription.SwimSubscriptionConfigParserPort;
import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(TestNameLoggerExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class AbstractSubscriptionStartupHandlerTest {

    private Instance<SwimSubscriptionConfigParserPort> configParsers;
    private Instance<SwimSubscriptionLifecyclePort> subscriptionServices;
    private Instance<SwimSubscriptionCountPort> repositories;
    private ReconciliationStateTracker stateManager;
    private Vertx vertx;
    private SwimSubscriptionConfigParserPort parser;
    private AbstractSubscriptionService service;
    private SwimSubscriptionCountPort repository;

    private AbstractSubscriptionStartupHandler handler;
    private boolean backgroundTaskRan;

    @BeforeEach
    void setUp() {
        configParsers = mock(Instance.class);
        subscriptionServices = mock(Instance.class);
        repositories = mock(Instance.class);
        stateManager = mock(ReconciliationStateTracker.class);
        vertx = mock(Vertx.class);
        parser = mock(SwimSubscriptionConfigParserPort.class);
        service = mock(AbstractSubscriptionService.class);
        repository = mock(SwimSubscriptionCountPort.class);

        when(configParsers.get()).thenReturn(parser);
        when(subscriptionServices.get()).thenReturn(service);
        when(repositories.get()).thenReturn(repository);

        backgroundTaskRan = false;
        when(vertx.executeBlocking(any(Callable.class), anyBoolean())).thenAnswer(inv -> {
            backgroundTaskRan = true;
            Callable<?> callable = inv.getArgument(0);
            callable.call();
            return Future.succeededFuture();
        });

        handler = new AbstractSubscriptionStartupHandler(
                configParsers, subscriptionServices, repositories,
                stateManager, vertx, false);
    }

    @Test
    void performStartup_exits_whenDatabaseConnectionFails() {
        when(repository.countTotalSubscriptions()).thenThrow(new RuntimeException("DB down"));

        handler.performStartup();

        assertThat(backgroundTaskRan).isFalse();
    }

    @Test
    void performStartup_doesNotMarkReconciled_whenNoSubscriptionsConfigured() {
        when(repository.countTotalSubscriptions()).thenReturn(0L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of());

        handler.performStartup();

        verify(stateManager, never()).markAsReconciled();
    }

    @Test
    void performStartup_marksReconciled_whenFullyReconciled() {
        when(repository.countTotalSubscriptions()).thenReturn(0L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1", "sub-2"));
        when(service.reconcileCreate(any())).thenReturn(new ReconciliationResult(2, 2, 0, 0));
        when(stateManager.isReconciled()).thenReturn(true);

        handler.performStartup();

        verify(stateManager).markAsReconciled();
        verify(service).registerAllActiveConsumers();
    }

    @Test
    void performStartup_doesNotMarkReconciled_whenPartiallyReconciled() {
        when(repository.countTotalSubscriptions()).thenReturn(0L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1", "sub-2"));
        when(service.reconcileCreate(any())).thenReturn(new ReconciliationResult(2, 1, 1, 0));

        handler.performStartup();

        verify(stateManager, never()).markAsReconciled();
        verify(service, never()).registerAllActiveConsumers();
    }

    @Test
    void performStartup_handlesException_gracefully() {
        when(repository.countTotalSubscriptions()).thenReturn(0L);
        when(parser.parseDesiredSubscriptions()).thenReturn(List.of("sub-1"));
        when(service.reconcileCreate(any())).thenThrow(new RuntimeException("reconcile error"));

        handler.performStartup();

        verify(stateManager, never()).markAsReconciled();
    }
}
