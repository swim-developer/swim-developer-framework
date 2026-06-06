package com.github.swim_developer.framework.provider.application.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class AbstractSubscriptionMetrics {

    protected final MeterRegistry registry;

    @Inject
    protected AbstractSubscriptionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    
    @PostConstruct
    void init() {
        Gauge.builder(getServiceName() + "_active_subscriptions", this, AbstractSubscriptionMetrics::countActiveSubscriptions)
                .description("Number of active subscriptions")
                .register(registry);
        registerCustomGauges();
        log.info("{} subscription metrics registered", getServiceName());
    }

    
    public void updateGauges() {
        try {
            performGaugeUpdate();
        } catch (Exception e) {
            log.warn("Failed to update subscription gauges", e);
        }
    }

    
    protected abstract String getServiceName();

    
    protected abstract double countActiveSubscriptions();

    
    protected abstract void registerCustomGauges();

    
    protected abstract void performGaugeUpdate();
}
