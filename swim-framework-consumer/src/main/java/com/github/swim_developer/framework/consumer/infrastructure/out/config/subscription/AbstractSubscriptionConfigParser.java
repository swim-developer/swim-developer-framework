package com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractSubscriptionConfigParser<T> implements SwimSubscriptionConfigParserPort {

    protected final ObjectMapper objectMapper;

    @Inject
    protected AbstractSubscriptionConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> parseDesiredSubscriptions() {
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, getSubscriptionType());
            return (List<Object>) objectMapper.readValue(getSubscriptionsJson(), listType);
        } catch (Exception e) {
            log.error("Failed to parse {} subscriptions", getServiceName(), e);
            return Collections.emptyList();
        }
    }

    protected abstract String getSubscriptionsJson();

    protected abstract Class<T> getSubscriptionType();

    protected abstract String getServiceName();
}
