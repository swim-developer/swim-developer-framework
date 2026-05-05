package com.github.swim_developer.framework.consumer.infrastructure.out.client;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.ResilienceConfig;
import com.github.swim_developer.framework.application.model.SubscriptionManagerConfig;
import com.github.swim_developer.framework.domain.exception.ProviderClientException;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(TestNameLoggerExtension.class)
class AbstractSubscriptionManagerClientRegistryTest {

    interface StubClient {}

    private StubClient fixedClient;
    private StubClientRegistry registry;

    @BeforeEach
    void setUp() {
        fixedClient = mock(StubClient.class);
        registry = new StubClientRegistry(fixedClient);
    }

    @Test
    void getOrCreate_returnsCachedClient_onSecondCall() {
        ProviderConfiguration provider = provider("p1", 1, 0);

        StubClient first = registry.getOrCreate(provider);
        StubClient second = registry.getOrCreate(provider);

        assertThat(first).isSameAs(second).isSameAs(fixedClient);
    }

    @Test
    void getOrCreate_createsSeparateClients_forDifferentProviders() {
        StubClient client2 = mock(StubClient.class);
        StubClientRegistry registry2 = new StubClientRegistry(client2);

        StubClient c1 = registry.getOrCreate(provider("provA", 1, 0));
        StubClient c2 = registry2.getOrCreate(provider("provB", 1, 0));

        assertThat(c1).isNotSameAs(c2);
    }

    @Test
    void size_returnsNumberOfCachedClients() {
        assertThat(registry.size()).isZero();
        registry.getOrCreate(provider("p1", 1, 0));
        assertThat(registry.size()).isEqualTo(1);
        registry.getOrCreate(provider("p2", 1, 0));
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void clear_removesAllClients() {
        registry.getOrCreate(provider("p1", 1, 0));
        registry.getOrCreate(provider("p2", 1, 0));
        registry.clear();
        assertThat(registry.size()).isZero();
    }

    @Test
    void executeWithRetry_returnsResult_onFirstSuccess() {
        ProviderConfiguration provider = provider("p1", 3, 1);

        String result = registry.executeWithRetry(provider, "create", () -> "OK");

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void executeWithRetry_retriesAndSucceeds_afterTransientFailure() {
        ProviderConfiguration provider = provider("p1", 3, 1);
        int[] calls = {0};

        String result = registry.executeWithRetry(provider, "create", () -> {
            if (++calls[0] < 2) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void executeWithRetry_propagates4xx_withoutRetry() {
        ProviderConfiguration provider = provider("p1", 3, 1);
        int[] calls = {0};

        assertThatThrownBy(() ->
                registry.executeWithRetry(provider, "create", () -> {
                    calls[0]++;
                    throw new WebApplicationException(404);
                })
        ).isInstanceOf(WebApplicationException.class);

        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void executeWithRetry_throwsProviderClientException_afterAllAttemptsExhausted() {
        ProviderConfiguration provider = provider("p1", 2, 1);

        assertThatThrownBy(() ->
                registry.executeWithRetry(provider, "create", () -> {
                    throw new RuntimeException("fail");
                })
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void executeWithRetry_throwsProviderClientException_whenCircuitBreakerOpen() {
        ProviderConfiguration provider = provider("cb-provider", 1, 1);
        for (int i = 0; i < 5; i++) {
            registry.getCircuitBreaker().recordFailure("cb-provider");
        }

        assertThatThrownBy(() -> registry.executeWithRetry(provider, "op", () -> "x"))
                .isInstanceOf(ProviderClientException.class)
                .hasMessageContaining("Circuit breaker OPEN");
    }

    @Test
    void executeWithRetryVoid_runsAction_onSuccess() {
        ProviderConfiguration provider = provider("p1", 1, 0);
        boolean[] ran = {false};

        registry.executeWithRetryVoid(provider, "delete", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
    }

    @Test
    void calculateBackoffDelay_doublesWithEachAttempt() {
        assertThat(AbstractSubscriptionManagerClientRegistry.calculateBackoffDelay(1, 100)).isEqualTo(100);
        assertThat(AbstractSubscriptionManagerClientRegistry.calculateBackoffDelay(2, 100)).isEqualTo(200);
        assertThat(AbstractSubscriptionManagerClientRegistry.calculateBackoffDelay(3, 100)).isEqualTo(400);
    }

    @Test
    void calculateBackoffDelay_capsAt30Seconds() {
        long delay = AbstractSubscriptionManagerClientRegistry.calculateBackoffDelay(10, 1000);
        assertThat(delay).isEqualTo(30_000L);
    }

    private static ProviderConfiguration provider(String id, int maxAttempts, long delayMs) {
        ResilienceConfig resilience = new ResilienceConfig(0, 0, maxAttempts, delayMs);
        SubscriptionManagerConfig sm = new SubscriptionManagerConfig("https://sm.test", null, resilience);
        return new ProviderConfiguration(id, sm, null);
    }

    private static class StubClientRegistry extends AbstractSubscriptionManagerClientRegistry<StubClient> {
        private final StubClient client;

        StubClientRegistry(StubClient client) {
            this.client = client;
        }

        @Override
        protected Class<StubClient> getClientClass() {
            return StubClient.class;
        }

        @Override
        protected StubClient buildClient(ProviderConfiguration provider) {
            return client;
        }

        @Override
        public String querySubscriptionStatus(String subscriptionId, ProviderConfiguration provider) {
            return "ACTIVE";
        }
    }
}
