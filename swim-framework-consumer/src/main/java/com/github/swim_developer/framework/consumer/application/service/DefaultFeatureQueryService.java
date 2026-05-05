package com.github.swim_developer.framework.consumer.application.service;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.port.out.SwimProviderConfigPort;
import com.github.swim_developer.framework.consumer.application.port.in.SwimQueryFeaturesPort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimRemoteFeatureQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class DefaultFeatureQueryService implements SwimQueryFeaturesPort {

    private final SwimProviderConfigPort providerConfigPort;
    private final SwimRemoteFeatureQueryPort featureQueryPort;

    @Inject
    public DefaultFeatureQueryService(SwimProviderConfigPort providerConfigPort,
                                      SwimRemoteFeatureQueryPort featureQueryPort) {
        this.providerConfigPort = providerConfigPort;
        this.featureQueryPort = featureQueryPort;
    }

    @Override
    public String queryFeatures(String typeName, String filter, String validTime, String providerId) {
        ProviderConfiguration provider = providerConfigPort.findByProviderIdOrDefault(providerId)
                .orElseThrow(() -> new IllegalStateException("No provider configured for providerId: " + providerId));

        log.info("WFS GetFeature → provider '{}' ({}), typeName={}, validTime={}",
                provider.providerId(), provider.subscriptionManager().url(), typeName, validTime);

        return featureQueryPort.queryFeatures(typeName, filter, validTime, provider);
    }
}
