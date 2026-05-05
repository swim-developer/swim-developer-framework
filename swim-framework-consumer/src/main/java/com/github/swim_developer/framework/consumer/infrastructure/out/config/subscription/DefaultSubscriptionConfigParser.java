package com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@DefaultBean
@ApplicationScoped
@SuppressWarnings("unchecked")
public class DefaultSubscriptionConfigParser extends AbstractSubscriptionConfigParser<Object> {

    @ConfigProperty(name = "swim.subscriptions", defaultValue = "[]")
    String subscriptionsJson;

    @ConfigProperty(name = "swim.service.name", defaultValue = "SWIM")
    String serviceName;

    @ConfigProperty(name = "swim.subscriptions.command-type", defaultValue = "java.util.LinkedHashMap")
    String commandTypeName;

    protected DefaultSubscriptionConfigParser() {
        super(null);
    }

    @Inject
    public DefaultSubscriptionConfigParser(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected String getSubscriptionsJson() {
        return subscriptionsJson;
    }

    @Override
    protected Class<Object> getSubscriptionType() {
        try {
            return (Class<Object>) Thread.currentThread()
                    .getContextClassLoader().loadClass(commandTypeName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load subscription command type: " + commandTypeName, e);
        }
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }
}
