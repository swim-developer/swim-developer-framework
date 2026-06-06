package com.github.swim_developer.framework.consumer.unit.config.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.consumer.infrastructure.out.config.provider.ProviderConfigParser;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class ProviderConfigParserTest {

    private static final String SINGLE_PROVIDER_JSON = """
            [
              {
                "providerId": "provider-a",
                "subscriptionManager": {
                  "url": "https://sm.example.com"
                },
                "amqpBroker": {
                  "host": "broker.example.com",
                  "port": 5671,
                  "sslEnabled": true
                }
              }
            ]
            """;

    private static final String TWO_PROVIDERS_JSON = """
            [
              {
                "providerId": "alpha",
                "subscriptionManager": { "url": "https://alpha.example.com" },
                "amqpBroker": { "host": "alpha-broker.example.com", "port": 5671, "sslEnabled": true }
              },
              {
                "providerId": "beta",
                "subscriptionManager": { "url": "https://beta.example.com" },
                "amqpBroker": { "host": "beta-broker.example.com", "port": 5671, "sslEnabled": false }
              }
            ]
            """;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void parseProviders_returnsSingleProvider() {
        var parser = new ProviderConfigParser(objectMapper, SINGLE_PROVIDER_JSON);
        var providers = parser.parseProviders();

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0).providerId()).isEqualTo("provider-a");
        assertThat(providers.get(0).subscriptionManager().url()).isEqualTo("https://sm.example.com");
    }

    @Test
    void parseProviders_returnsEmptyListForEmptyJson() {
        var parser = new ProviderConfigParser(objectMapper, "[]");
        assertThat(parser.parseProviders()).isEmpty();
    }

    @Test
    void parseProviders_returnsEmptyListForInvalidJson() {
        var parser = new ProviderConfigParser(objectMapper, "not-json");
        assertThat(parser.parseProviders()).isEmpty();
    }

    @Test
    void getProviderMap_buildsMapFromProviders() {
        var parser = new ProviderConfigParser(objectMapper, TWO_PROVIDERS_JSON);
        var map = parser.getProviderMap();

        assertThat(map).hasSize(2).containsKeys("alpha", "beta");
    }

    @Test
    void findByProviderId_returnsCorrectProvider() {
        var parser = new ProviderConfigParser(objectMapper, TWO_PROVIDERS_JSON);
        Optional<ProviderConfiguration> result = parser.findByProviderId("beta");

        assertThat(result).isPresent();
        assertThat(result.get().providerId()).isEqualTo("beta");
    }

    @Test
    void findByProviderId_returnsEmptyForUnknownId() {
        var parser = new ProviderConfigParser(objectMapper, SINGLE_PROVIDER_JSON);
        assertThat(parser.findByProviderId("nonexistent")).isEmpty();
    }

    @Test
    void findByProviderId_returnsEmptyForNullId() {
        var parser = new ProviderConfigParser(objectMapper, SINGLE_PROVIDER_JSON);
        assertThat(parser.findByProviderId(null)).isEmpty();
    }

    @Test
    void findByProviderIdOrDefault_returnsSingleProviderWhenIdIsNull() {
        var parser = new ProviderConfigParser(objectMapper, SINGLE_PROVIDER_JSON);
        Optional<ProviderConfiguration> result = parser.findByProviderIdOrDefault(null);

        assertThat(result).isPresent();
        assertThat(result.get().providerId()).isEqualTo("provider-a");
    }

    @Test
    void findByProviderIdOrDefault_returnsEmptyWhenMultipleProvidersAndNullId() {
        var parser = new ProviderConfigParser(objectMapper, TWO_PROVIDERS_JSON);
        assertThat(parser.findByProviderIdOrDefault(null)).isEmpty();
    }

    @Test
    void getProviderMap_isIdempotent_doubleCheckedLocking() {
        var parser = new ProviderConfigParser(objectMapper, SINGLE_PROVIDER_JSON);

        var map1 = parser.getProviderMap();
        var map2 = parser.getProviderMap();

        assertThat(map1).hasSize(1);
        assertThat(map2).hasSize(1);
        assertThat(map1.keySet()).isEqualTo(map2.keySet());
    }
}
