package com.github.swim_developer.framework.consumer.infrastructure.in.rest;

import com.github.swim_developer.framework.consumer.application.port.in.SwimQueryFeaturesPort;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractFeatureResourceTest {

    private SwimQueryFeaturesPort queryFeaturesPort;
    private AbstractFeatureResource resource;

    @BeforeEach
    void setUp() {
        queryFeaturesPort = mock(SwimQueryFeaturesPort.class);
        resource = new AbstractFeatureResource(queryFeaturesPort) {};
    }

    @Test
    void getFeatures_returns200WithXmlOnSuccess() {
        when(queryFeaturesPort.queryFeatures(any(), any(), any(), any())).thenReturn("<features/>");

        Response response = resource.getFeatures("FeatureType", null, null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo("<features/>");
    }

    @Test
    void getFeatures_returns503WhenNoProviderConfigured() {
        when(queryFeaturesPort.queryFeatures(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("No provider configured"));

        Response response = resource.getFeatures("FeatureType", null, null, null);

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void getFeatures_returns502OnProviderError() {
        when(queryFeaturesPort.queryFeatures(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("upstream failed"));

        Response response = resource.getFeatures("FeatureType", null, null, null);

        assertThat(response.getStatus()).isEqualTo(502);
    }
}
