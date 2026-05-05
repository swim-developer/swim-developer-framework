package com.github.swim_developer.framework.consumer.unit.application.service;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.SubscriptionManagerConfig;
import com.github.swim_developer.framework.application.port.out.SwimProviderConfigPort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimRemoteFeatureQueryPort;
import com.github.swim_developer.framework.consumer.application.service.DefaultFeatureQueryService;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class DefaultFeatureQueryServiceTest {

    private SwimProviderConfigPort providerConfigPort;
    private SwimRemoteFeatureQueryPort featureQueryPort;
    private ProviderConfiguration provider;
    private DefaultFeatureQueryService service;

    @BeforeEach
    void setUp() {
        providerConfigPort = mock(SwimProviderConfigPort.class);
        featureQueryPort = mock(SwimRemoteFeatureQueryPort.class);
        provider = ProviderConfiguration.builder()
                .providerId("test-provider")
                .subscriptionManager(SubscriptionManagerConfig.builder()
                        .url("https://sm.test.local/api/v1")
                        .build())
                .build();
        service = new DefaultFeatureQueryService(providerConfigPort, featureQueryPort);
    }

    @Test
    void queryFeatures_delegatesToRemotePortWithResolvedProvider() {
        String typeName = "RunwayDirection";
        String filter = "icao=EBBR";
        String validTime = "2025-12-01T00:00:00Z";
        String providerId = "eurocontrol-provider";
        String expectedXml = "<AIXMBasicMessage/>";

        when(providerConfigPort.findByProviderIdOrDefault(providerId)).thenReturn(Optional.of(provider));
        when(featureQueryPort.queryFeatures(typeName, filter, validTime, provider)).thenReturn(expectedXml);

        String result = service.queryFeatures(typeName, filter, validTime, providerId);

        assertThat(result).isEqualTo(expectedXml);
        verify(featureQueryPort).queryFeatures(typeName, filter, validTime, provider);
    }

    @Test
    void queryFeatures_throwsWhenNoProviderConfigured() {
        when(providerConfigPort.findByProviderIdOrDefault("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queryFeatures("RunwayDirection", null, null, "unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void queryFeatures_withNullProviderId_usesDefaultProvider() {
        String typeName = "AirportHeliport";
        String expectedXml = "<features/>";

        when(providerConfigPort.findByProviderIdOrDefault(null)).thenReturn(Optional.of(provider));
        when(featureQueryPort.queryFeatures(typeName, null, null, provider)).thenReturn(expectedXml);

        String result = service.queryFeatures(typeName, null, null, null);

        assertThat(result).isEqualTo(expectedXml);
    }

    @Test
    void queryFeatures_passesAllParametersToRemotePort() {
        String typeName = "Obstacle";
        String filter = "id=OBS-001";
        String validTime = "2025-06-15T12:00:00Z";
        String providerId = "ead-provider";

        when(providerConfigPort.findByProviderIdOrDefault(providerId)).thenReturn(Optional.of(provider));
        when(featureQueryPort.queryFeatures(typeName, filter, validTime, provider)).thenReturn("<result/>");

        service.queryFeatures(typeName, filter, validTime, providerId);

        verify(featureQueryPort).queryFeatures(typeName, filter, validTime, provider);
    }
}
