package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DefaultEventProcessorConfig implements SwimEventProcessorConfig {

    private final String servicePrefix;
    private final SwimIdempotencyPort idempotencyCache;
    private final SwimDeadLetterPort deadLetterService;

    protected DefaultEventProcessorConfig() {
        this.servicePrefix = null;
        this.idempotencyCache = null;
        this.deadLetterService = null;
    }

    @Inject
    public DefaultEventProcessorConfig(@ConfigProperty(name = "swim.service.name") String servicePrefix,
                                       SwimIdempotencyPort idempotencyCache,
                                       SwimDeadLetterPort deadLetterService) {
        this.servicePrefix = servicePrefix;
        this.idempotencyCache = idempotencyCache;
        this.deadLetterService = deadLetterService;
    }

    @Override
    public String getServicePrefix() { return servicePrefix; }

    @Override
    public SwimIdempotencyPort getIdempotencyCache() { return idempotencyCache; }

    @Override
    public SwimDeadLetterPort getDeadLetterService() { return deadLetterService; }
}
