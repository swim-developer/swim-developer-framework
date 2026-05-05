package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.SubscriptionManagerConfig;
import com.github.swim_developer.framework.domain.exception.SubscriptionConflictException;
import com.github.swim_developer.framework.domain.exception.SubscriptionNotFoundException;
import com.github.swim_developer.framework.domain.model.SwimConsumerSubscription;
import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class SubscriptionExecutorsTest {

    private StubService service;

    @BeforeEach
    void setUp() {
        service = new StubService();
    }

    // ── SubscriptionReconciliationExecutor ─────────────────────────────────────

    @Test
    void reconcileCreate_skipsAlreadyExistingSubscriptions() {
        service.addExisting("desired-1");

        ReconciliationResult result = service.reconcileCreate(List.of("desired-1"));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void reconcileCreate_createsAndActivatesNewSubscriptions() {
        ReconciliationResult result = service.reconcileCreate(List.of("desired-new"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(service.registeredConsumers).contains("sub-desired-new");
    }

    @Test
    void reconcileCreate_tracksFailedSubscriptions() {
        service.createException = new RuntimeException("provider error");

        ReconciliationResult result = service.reconcileCreate(List.of("desired-fail"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.succeeded()).isZero();
    }

    @Test
    void reconcileCreate_emptyList_returnsEmptyResult() {
        ReconciliationResult result = service.reconcileCreate(List.of());

        assertThat(result.desired()).isZero();
        assertThat(result.succeeded()).isZero();
    }

    @Test
    void reconcileDelete_deletesSubscriptionsNotInDesiredList() {
        service.store.put("old-sub", new TestSub("old-sub", "q-old", "p1"));
        service.desiredAlwaysFalseForId.add("old-sub");

        service.reconcileDelete(List.of("desired-other"));

        assertThat(service.unregisteredConsumers).contains("old-sub");
        assertThat(service.deletedRemote).contains("old-sub");
        assertThat(service.deletedLocal).contains("old-sub");
    }

    @Test
    void reconcileDelete_keepsSubscriptionsThatAreStillDesired() {
        service.store.put("keep-sub", new TestSub("keep-sub", "q-keep", "p1"));

        service.reconcileDelete(List.of("desired-keep"));

        assertThat(service.deletedLocal).doesNotContain("keep-sub");
    }

    // ── SubscriptionLifecycleExecutor ──────────────────────────────────────────

    @Test
    void deleteById_removesSubscription() {
        service.store.put("sub-del", new TestSub("sub-del", "q-del", "p1"));

        service.deleteSubscriptionById("sub-del");

        assertThat(service.unregisteredConsumers).contains("sub-del");
        assertThat(service.deletedRemote).contains("sub-del");
        assertThat(service.deletedLocal).contains("sub-del");
    }

    @Test
    void deleteById_throwsForUnknownSubscription() {
        assertThatThrownBy(() -> service.deleteSubscriptionById("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void pause_updatesStatusToPaused() {
        service.store.put("sub-p", new TestSub("sub-p", "q-p", "p1"));

        service.pauseSubscription("sub-p");

        assertThat(service.pausedConsumers).contains("sub-p");
        assertThat(service.statusUpdates).containsEntry("sub-p", "PAUSED");
    }

    @Test
    void pause_throwsForUnknownSubscription() {
        assertThatThrownBy(() -> service.pauseSubscription("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resume_registersConsumerAndActivates() {
        service.store.put("sub-r", new TestSub("sub-r", "q-r", "p1"));

        service.resumeSubscription("sub-r");

        assertThat(service.registeredConsumers).contains("sub-r");
        assertThat(service.statusUpdates).containsKey("sub-r");
    }

    @Test
    void resume_throwsForUnknownSubscription() {
        assertThatThrownBy(() -> service.resumeSubscription("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── SubscriptionActivationExecutor ─────────────────────────────────────────

    @Test
    void activateSubscription_registersAndActivates() {
        ProviderConfiguration provider = buildProvider();

        service.activateSubscription("sub-act", "q-act", provider);

        assertThat(service.registeredConsumers).contains("sub-act");
        assertThat(service.statusUpdates).containsKey("sub-act");
    }

    @Test
    void activateSubscription_unregistersOnActivationFailure() {
        service.updateStatusException = new RuntimeException("remote error");
        ProviderConfiguration providerConfig = buildProvider();

        assertThatThrownBy(() -> service.activateSubscription("sub-fail", "q-fail", providerConfig))
                .isInstanceOf(RuntimeException.class);

        assertThat(service.unregisteredConsumers).contains("sub-fail");
    }

    @Test
    void activateSubscription_handleSubscriptionLostOn404() {
        service.store.put("sub-404", new TestSub("sub-404", "q-404", "p1"));
        service.updateStatusException = new SubscriptionNotFoundException("sub-404");
        ProviderConfiguration providerConfig = buildProvider();

        assertThatThrownBy(() -> service.activateSubscription("sub-404", "q-404", providerConfig))
                .isInstanceOf(SubscriptionNotFoundException.class);

        assertThat(service.lostSubscriptions).contains("sub-404");
    }

    // ── SubscriptionLossRecoveryExecutor ───────────────────────────────────────

    @Test
    void handleSubscriptionLost_recreatesSubscriptionFromDesiredConfig() {
        TestSub subscription = new TestSub("sub-lost", "q-lost", "p1");
        service.desiredConfigForSub = "desired-lost";

        service.handleSubscriptionLost("sub-lost", subscription);

        assertThat(service.statusUpdates).containsEntry("sub-lost", "INVALID");
        assertThat(service.deletedLocal).contains("sub-lost");
        assertThat(service.registeredConsumers).contains("sub-desired-lost");
    }

    @Test
    void handleSubscriptionLost_noopWhenDesiredConfigNotAvailable() {
        TestSub subscription = new TestSub("sub-noop", "q-noop", "p1");
        service.desiredConfigForSub = null;

        service.handleSubscriptionLost("sub-noop", subscription);

        assertThat(service.deletedLocal).contains("sub-noop");
        assertThat(service.registeredConsumers).isEmpty();
    }

    @Test
    void handleSubscriptionLost_syncFromProviderOnConflict() {
        TestSub subscription = new TestSub("sub-409", "q-409", "p1");
        service.desiredConfigForSub = "desired-409";
        service.reconcileCreateException = new SubscriptionConflictException("409");

        service.handleSubscriptionLost("sub-409", subscription);

        assertThat(service.syncedDesiredConfigs).contains("desired-409");
    }

    // ── SubscriptionActiveConsumerRegistration ─────────────────────────────────

    @Test
    void registerAllActiveConsumers_registersEachActiveSubscription() {
        service.activeSubscriptions.add(new TestSub("sub-a1", "q-a1", "p1"));
        service.activeSubscriptions.add(new TestSub("sub-a2", "q-a2", "p1"));

        service.registerAllActiveConsumers();

        assertThat(service.registeredConsumers).containsExactlyInAnyOrder("sub-a1", "sub-a2");
        assertThat(service.allConsumersRegisteredCalled).isTrue();
    }

    @Test
    void registerAllActiveConsumers_noop_whenNoActiveSubscriptions() {
        service.registerAllActiveConsumers();

        assertThat(service.registeredConsumers).isEmpty();
        assertThat(service.allConsumersRegisteredCalled).isFalse();
    }

    // ── AbstractSubscriptionService direct methods ──────────────────────────────

    @Test
    void onActivationFailed_updatesStatusToFailedActivation() {
        service.store.put("sub-fg", new TestSub("sub-fg", "q-fg", "p1"));

        service.onActivationFailed("sub-fg", "q-fg", buildProvider());

        assertThat(service.statusUpdates).containsEntry("sub-fg", "FAILED_ACTIVATION");
    }

    @Test
    void onActivationFailed_noopWhenSubscriptionAlreadyGone() {
        service.onActivationFailed("sub-gone", "q-gone", buildProvider());

        assertThat(service.statusUpdates).doesNotContainKey("sub-gone");
    }

    @Test
    void toDesiredConfig_defaultReturnsEmpty() {
        assertThat(service.toDesiredConfig(new TestSub("x", "q", "p"))).isEmpty();
    }

    @Test
    void syncFromProvider_defaultImplementationDoesNotThrow() {
        service.syncFromProvider("some-desired");

        assertThat(service.syncedDesiredConfigs).containsExactly("some-desired");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ProviderConfiguration buildProvider() {
        return ProviderConfiguration.builder()
                .providerId("p1")
                .subscriptionManager(SubscriptionManagerConfig.builder().url("https://sm.example.com").build())
                .build();
    }

    // ── Stub implementation ────────────────────────────────────────────────────

    record TestSub(String subscriptionId, String queueName, String providerId)
            implements SwimConsumerSubscription {
        public String getSubscriptionId() { return subscriptionId; }
        public String getQueueName() { return queueName; }
        public String getProviderId() { return providerId; }
    }

    static class StubService extends AbstractSubscriptionService<String, TestSub> {

        final Map<String, TestSub> store = new HashMap<>();
        final List<String> registeredConsumers = new ArrayList<>();
        final List<String> unregisteredConsumers = new ArrayList<>();
        final List<String> pausedConsumers = new ArrayList<>();
        final List<String> deletedLocal = new ArrayList<>();
        final List<String> deletedRemote = new ArrayList<>();
        final Map<String, String> statusUpdates = new HashMap<>();
        final List<String> lostSubscriptions = new ArrayList<>();
        final List<String> desiredAlwaysFalseForId = new ArrayList<>();
        final List<TestSub> activeSubscriptions = new ArrayList<>();
        final List<String> syncedDesiredConfigs = new ArrayList<>();
        boolean allConsumersRegisteredCalled = false;

        RuntimeException createException;
        RuntimeException reconcileCreateException;
        RuntimeException updateStatusException;
        String desiredConfigForSub;

        private final ProviderConfiguration defaultProvider = ProviderConfiguration.builder()
                .providerId("p1")
                .subscriptionManager(SubscriptionManagerConfig.builder().url("https://sm.example.com").build())
                .build();

        void addExisting(String desired) {
            store.put("sub-" + desired, new TestSub("sub-" + desired, "queue-" + desired, "p1"));
        }

        @Override public ReconciliationResult reconcileCreate(List<String> desiredSubscriptions) {
            if (reconcileCreateException != null) throw reconcileCreateException;
            return super.reconcileCreate(desiredSubscriptions);
        }

        @Override protected TestSub callCreateAndPersist(String desired) {
            if (createException != null) throw createException;
            TestSub sub = new TestSub("sub-" + desired, "queue-" + desired, "p1");
            store.put(sub.subscriptionId(), sub);
            return sub;
        }

        @Override protected String callUpdateStatus(String subscriptionId, String newStatus) {
            if (updateStatusException != null) throw updateStatusException;
            statusUpdates.put(subscriptionId, newStatus);
            return newStatus;
        }

        @Override protected void callDeleteRemoteSubscription(String subscriptionId) {
            deletedRemote.add(subscriptionId);
        }

        @Override protected boolean existsLocally(String desired) {
            return store.containsKey("sub-" + desired);
        }

        @Override public Optional<TestSub> findBySubscriptionId(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override protected List<TestSub> loadDeclaredSubscriptions() {
            return new ArrayList<>(store.values());
        }

        @Override protected boolean isStillDesired(TestSub current, List<String> desired) {
            return !desiredAlwaysFalseForId.contains(current.subscriptionId());
        }

        @Override protected void deleteLocalSubscription(String subscriptionId) {
            deletedLocal.add(subscriptionId);
            store.remove(subscriptionId);
        }

        @Override protected void updateLocalStatus(String subscriptionId, String status) {
            statusUpdates.put(subscriptionId, status);
        }

        @Override protected String describeDesired(String desired) { return desired; }

        @Override public ProviderConfiguration resolveProvider(String providerId) { return defaultProvider; }

        @Override protected List<TestSub> findActiveSubscriptions() { return activeSubscriptions; }

        @Override
        protected void registerAmqpConsumer(String subscriptionId, String queueName, ProviderConfiguration provider) {
            registeredConsumers.add(subscriptionId);
        }

        @Override
        protected void pauseAmqpConsumer(String subscriptionId) {
            pausedConsumers.add(subscriptionId);
        }

        @Override
        protected void unregisterAmqpConsumer(String subscriptionId) {
            unregisteredConsumers.add(subscriptionId);
        }

        @Override
        protected void onAllConsumersRegistered() {
            allConsumersRegisteredCalled = true;
        }

        @Override
        public void handleSubscriptionLost(String subscriptionId, TestSub subscription) {
            lostSubscriptions.add(subscriptionId);
            super.handleSubscriptionLost(subscriptionId, subscription);
        }

        @Override
        protected Optional<String> toDesiredConfig(TestSub subscription) {
            return Optional.ofNullable(desiredConfigForSub);
        }

        @Override
        protected void syncFromProvider(String desiredConfig) {
            syncedDesiredConfigs.add(desiredConfig);
        }

        @Override
        public void onActivationFailed(String subscriptionId, String queueName, ProviderConfiguration provider) {
            super.onActivationFailed(subscriptionId, queueName, provider);
        }
    }
}
