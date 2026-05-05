package com.github.swim_developer.framework.consumer.infrastructure.out.config.provider;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class ProviderConfigParser implements com.github.swim_developer.framework.application.port.out.SwimProviderConfigPort {

    private final ObjectMapper objectMapper;
    private final String providersJson;
    private final Map<String, ProviderConfiguration> providerMap = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    @Inject
    public ProviderConfigParser(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "swim.providers", defaultValue = "[]") String providersJson) {
        this.objectMapper = objectMapper;
        this.providersJson = providersJson;
    }

    public List<ProviderConfiguration> parseProviders() {
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ProviderConfiguration.class);
            return objectMapper.readValue(providersJson, listType);
        } catch (Exception e) {
            log.error("Failed to parse swim.providers configuration", e);
            return Collections.emptyList();
        }
    }

    public Optional<ProviderConfiguration> findByProviderId(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getProviderMap().get(providerId));
    }

    public Optional<ProviderConfiguration> findByProviderIdOrDefault(String providerId) {
        if (providerId != null) {
            return findByProviderId(providerId);
        }
        Map<String, ProviderConfiguration> map = getProviderMap();
        if (map.size() == 1) {
            return Optional.of(map.values().iterator().next());
        }
        return Optional.empty();
    }

    public Map<String, ProviderConfiguration> getProviderMap() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    parseProviders().forEach(p -> providerMap.put(p.providerId(), p));
                    initialized = true;
                }
            }
        }
        return Collections.unmodifiableMap(providerMap);
    }
}
