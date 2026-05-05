package com.github.swim_developer.framework.application.model;

import lombok.Builder;

@Builder
public record SubscriptionManagerConfig(
    String url,
    TlsConfig tls,
    ResilienceConfig resilience
) {
    private static final ResilienceConfig DEFAULT_RESILIENCE = new ResilienceConfig(0, 0, 0, 0);

    public ResilienceConfig effectiveResilience() {
        return resilience != null ? resilience : DEFAULT_RESILIENCE;
    }
}
