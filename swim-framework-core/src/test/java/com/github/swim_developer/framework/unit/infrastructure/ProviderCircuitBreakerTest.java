package com.github.swim_developer.framework.unit.infrastructure;

import com.github.swim_developer.framework.infrastructure.out.resilience.ProviderCircuitBreaker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa comportamento crítico de ProviderCircuitBreaker.
 *
 * Garantias de Negócio:
 * 1. Circuit breaker abre após threshold de falhas consecutivas
 * 2. Circuit breaker fecha automaticamente após período de cooldown (HALF-OPEN)
 * 3. Sucesso após HALF-OPEN fecha o circuit breaker completamente
 * 4. Sucesso em qualquer estado reseta contador de falhas
 * 5. Reset manual limpa estado completamente
 * 6. Múltiplos providers são isolados (failure de um não afeta outro)
 */
@Tag("infrastructure")
class ProviderCircuitBreakerTest {

    @Test
    void circuitBreaker_startsInClosedState() {
        // GIVEN: Circuit breaker novo
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker();

        // WHEN/THEN: Inicia fechado (permite requests)
        assertThat(cb.isOpen("provider-1")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isZero();
    }

    @Test
    void circuitBreaker_opensAfterThreshold() {
        // GIVEN: Circuit breaker com threshold=5
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(5, Duration.ofSeconds(30));

        // WHEN: 5 falhas consecutivas
        for (int i = 0; i < 5; i++) {
            cb.recordFailure("provider-1");
        }

        // THEN: Circuit breaker abre
        assertThat(cb.isOpen("provider-1")).isTrue();
        assertThat(cb.getFailureCount("provider-1")).isEqualTo(5);
    }

    @Test
    void circuitBreaker_staysClosedBelowThreshold() {
        // GIVEN: Circuit breaker com threshold=5
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(5, Duration.ofSeconds(30));

        // WHEN: 4 falhas (abaixo do threshold)
        for (int i = 0; i < 4; i++) {
            cb.recordFailure("provider-1");
        }

        // THEN: Circuit breaker permanece fechado
        assertThat(cb.isOpen("provider-1")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isEqualTo(4);
    }

    @Test
    void circuitBreaker_halfOpenAfterCooldown() {
        // GIVEN: Circuit breaker aberto
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(3, Duration.ofMillis(100));
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.isOpen("provider-1")).isTrue();

        // WHEN: Cooldown period passa
        Instant afterCooldown = Instant.now().plusMillis(150);

        // THEN: Circuit breaker fecha (permite probe request)
        assertThat(cb.isOpen("provider-1", afterCooldown)).isFalse();
    }

    @Test
    void circuitBreaker_staysOpenDuringCooldown() {
        // GIVEN: Circuit breaker aberto
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(3, Duration.ofMinutes(10));
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.isOpen("provider-1")).isTrue();

        // WHEN: Cooldown ainda não passou
        Instant stillCoolingDown = Instant.now().plusSeconds(30);

        // THEN: Circuit breaker permanece aberto
        assertThat(cb.isOpen("provider-1", stillCoolingDown)).isTrue();
    }

    @Test
    void recordSuccess_resetsFailureCount() {
        // GIVEN: Provider com 2 falhas
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker();
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.getFailureCount("provider-1")).isEqualTo(2);

        // WHEN: Sucesso registrado
        cb.recordSuccess("provider-1");

        // THEN: Contador de falhas zerado
        assertThat(cb.getFailureCount("provider-1")).isZero();
        assertThat(cb.isOpen("provider-1")).isFalse();
    }

    @Test
    void recordSuccess_closesCircuitBreaker() {
        // GIVEN: Circuit breaker aberto
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(2, Duration.ofSeconds(10));
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.isOpen("provider-1")).isTrue();

        // WHEN: Sucesso registrado
        cb.recordSuccess("provider-1");

        // THEN: Circuit breaker fecha
        assertThat(cb.isOpen("provider-1")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isZero();
    }

    @Test
    void reset_clearsAllState() {
        // GIVEN: Circuit breaker aberto com falhas
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(2, Duration.ofMinutes(10));
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.isOpen("provider-1")).isTrue();

        // WHEN: Reset manual
        cb.reset("provider-1");

        // THEN: Estado completamente limpo
        assertThat(cb.isOpen("provider-1")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isZero();
    }

    @Test
    void multipleProviders_areIsolated() {
        // GIVEN: Circuit breaker com threshold=3
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(3, Duration.ofSeconds(30));

        // WHEN: Provider-1 tem 3 falhas (abre), Provider-2 tem 1 falha
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-2");

        // THEN: Provider-1 aberto, Provider-2 fechado
        assertThat(cb.isOpen("provider-1")).isTrue();
        assertThat(cb.isOpen("provider-2")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isEqualTo(3);
        assertThat(cb.getFailureCount("provider-2")).isEqualTo(1);
    }

    @Test
    void recordSuccess_hasNoEffectWhenNoFailures() {
        // GIVEN: Provider sem falhas
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker();

        // WHEN: Sucesso registrado
        cb.recordSuccess("provider-1");

        // THEN: Estado permanece fechado
        assertThat(cb.isOpen("provider-1")).isFalse();
        assertThat(cb.getFailureCount("provider-1")).isZero();
    }

    @Test
    void reset_hasNoEffectForNonExistentProvider() {
        // GIVEN: Circuit breaker vazio
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker();

        // WHEN: Reset para provider inexistente
        cb.reset("non-existent");

        // THEN: Estado permanece fechado e sem falhas
        assertThat(cb.isOpen("non-existent")).isFalse();
        assertThat(cb.getFailureCount("non-existent")).isZero();
    }

    @Test
    void circuitBreaker_reopensAfterFailureInHalfOpenState() {
        // GIVEN: Circuit breaker em HALF-OPEN (após cooldown)
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(2, Duration.ofMillis(50));
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");
        assertThat(cb.isOpen("provider-1")).isTrue();

        // Cooldown passa
        Instant afterCooldown = Instant.now().plusMillis(100);
        assertThat(cb.isOpen("provider-1", afterCooldown)).isFalse();

        // WHEN: Nova falha em HALF-OPEN
        cb.recordFailure("provider-1");
        cb.recordFailure("provider-1");

        // THEN: Circuit breaker reabre
        assertThat(cb.isOpen("provider-1")).isTrue();
    }

    @Test
    void usesDefaultThresholdAndCooldown() {
        // GIVEN: Circuit breaker com defaults
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker();

        // WHEN: 5 falhas (default threshold)
        for (int i = 0; i < 5; i++) {
            cb.recordFailure("provider-1");
        }

        // THEN: Circuit breaker abre
        assertThat(cb.isOpen("provider-1")).isTrue();
    }
}
