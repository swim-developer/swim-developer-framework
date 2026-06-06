package com.github.swim_developer.framework.unit.domain;

import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SwimSubscription;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa contrato padrão de SwimSubscription.
 *
 * Garantias de Negócio:
 * 1. isActive() retorna true APENAS quando status = ACTIVE
 * 2. getProviderName() retorna null por padrão (override opcional)
 * 3. getHeartbeatTopic() retorna null por padrão (override opcional)
 */
@Tag("domain")
class SwimSubscriptionContractTest {

    @Test
    void isActive_returnsTrueOnlyWhenStatusIsActive() {
        // GIVEN: Subscription com status ACTIVE
        TestSubscription activeSubscription = new TestSubscription(SubscriptionStatus.ACTIVE);

        // WHEN/THEN: isActive() retorna true
        assertThat(activeSubscription.isActive()).isTrue();
    }

    @Test
    void isActive_returnsFalseWhenStatusIsPaused() {
        // GIVEN: Subscription com status PAUSED
        TestSubscription pausedSubscription = new TestSubscription(SubscriptionStatus.PAUSED);

        // WHEN/THEN: isActive() retorna false
        assertThat(pausedSubscription.isActive()).isFalse();
    }

    @Test
    void isActive_returnsFalseWhenStatusIsTerminated() {
        // GIVEN: Subscription com status TERMINATED
        TestSubscription terminatedSubscription = new TestSubscription(SubscriptionStatus.TERMINATED);

        // WHEN/THEN: isActive() retorna false
        assertThat(terminatedSubscription.isActive()).isFalse();
    }

    @Test
    void isActive_returnsFalseWhenStatusIsDeleted() {
        // GIVEN: Subscription com status DELETED
        TestSubscription deletedSubscription = new TestSubscription(SubscriptionStatus.DELETED);

        // WHEN/THEN: isActive() retorna false
        assertThat(deletedSubscription.isActive()).isFalse();
    }

    @Test
    void isActive_returnsFalseWhenStatusIsInvalid() {
        // GIVEN: Subscription com status INVALID
        TestSubscription invalidSubscription = new TestSubscription(SubscriptionStatus.INVALID);

        // WHEN/THEN: isActive() retorna false
        assertThat(invalidSubscription.isActive()).isFalse();
    }

    @Test
    void defaultMethods_returnNullWhenNotOverridden() {
        // GIVEN: Subscription usando implementação padrão
        TestSubscription subscription = new TestSubscription(SubscriptionStatus.ACTIVE);

        // WHEN/THEN: Métodos default retornam null
        assertThat(subscription.getProviderName()).isNull();
        assertThat(subscription.getHeartbeatTopic()).isNull();
    }

    /**
     * Implementação mínima de SwimSubscription para testes.
     */
    private static class TestSubscription implements SwimSubscription<String> {
        private final SubscriptionStatus status;

        public TestSubscription(SubscriptionStatus status) {
            this.status = status;
        }

        @Override
        public UUID getSubscriptionId() {
            return UUID.randomUUID();
        }

        @Override
        public String getQueue() {
            return "test-queue";
        }

        @Override
        public QualityOfService getQos() {
            return QualityOfService.AT_LEAST_ONCE;
        }

        @Override
        public SubscriptionStatus getStatus() {
            return status;
        }

        @Override
        public Predicate<String> toFilter() {
            return s -> true;
        }
    }
}
