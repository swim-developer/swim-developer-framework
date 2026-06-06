package com.github.swim_developer.framework.unit.application.model;

import com.github.swim_developer.framework.application.model.ResilienceConfig;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("application")
@ExtendWith(TestNameLoggerExtension.class)
class ResilienceConfigTest {

    @Test
    void effectiveConnectTimeoutMs_returnsConfiguredValueWhenPositive() {
        ResilienceConfig config = ResilienceConfig.builder().connectTimeoutMs(3000).build();
        assertThat(config.effectiveConnectTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void effectiveConnectTimeoutMs_returnsDefaultWhenZero() {
        ResilienceConfig config = ResilienceConfig.builder().connectTimeoutMs(0).build();
        assertThat(config.effectiveConnectTimeoutMs())
                .isEqualTo(ResilienceConfig.DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Test
    void effectiveConnectTimeoutMs_returnsDefaultWhenNegative() {
        ResilienceConfig config = ResilienceConfig.builder().connectTimeoutMs(-500).build();
        assertThat(config.effectiveConnectTimeoutMs())
                .isEqualTo(ResilienceConfig.DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Test
    void effectiveReadTimeoutMs_returnsConfiguredValueWhenPositive() {
        ResilienceConfig config = ResilienceConfig.builder().readTimeoutMs(60000).build();
        assertThat(config.effectiveReadTimeoutMs()).isEqualTo(60000);
    }

    @Test
    void effectiveReadTimeoutMs_returnsDefaultWhenZero() {
        ResilienceConfig config = ResilienceConfig.builder().readTimeoutMs(0).build();
        assertThat(config.effectiveReadTimeoutMs())
                .isEqualTo(ResilienceConfig.DEFAULT_READ_TIMEOUT_MS);
    }

    @Test
    void effectiveRetryMaxAttempts_returnsConfiguredValueWhenPositive() {
        ResilienceConfig config = ResilienceConfig.builder().retryMaxAttempts(5).build();
        assertThat(config.effectiveRetryMaxAttempts()).isEqualTo(5);
    }

    @Test
    void effectiveRetryMaxAttempts_returnsDefaultWhenZero() {
        ResilienceConfig config = ResilienceConfig.builder().retryMaxAttempts(0).build();
        assertThat(config.effectiveRetryMaxAttempts())
                .isEqualTo(ResilienceConfig.DEFAULT_RETRY_MAX_ATTEMPTS);
    }

    @Test
    void effectiveRetryDelayMs_returnsConfiguredValueWhenPositive() {
        ResilienceConfig config = ResilienceConfig.builder().retryDelayMs(2000L).build();
        assertThat(config.effectiveRetryDelayMs()).isEqualTo(2000L);
    }

    @Test
    void effectiveRetryDelayMs_returnsDefaultWhenZero() {
        ResilienceConfig config = ResilienceConfig.builder().retryDelayMs(0L).build();
        assertThat(config.effectiveRetryDelayMs())
                .isEqualTo(ResilienceConfig.DEFAULT_RETRY_DELAY_MS);
    }

    @Test
    void defaults_haveReasonableValues() {
        assertThat(ResilienceConfig.DEFAULT_CONNECT_TIMEOUT_MS).isPositive();
        assertThat(ResilienceConfig.DEFAULT_READ_TIMEOUT_MS).isPositive();
        assertThat(ResilienceConfig.DEFAULT_RETRY_MAX_ATTEMPTS).isPositive();
        assertThat(ResilienceConfig.DEFAULT_RETRY_DELAY_MS).isPositive();
    }
}
