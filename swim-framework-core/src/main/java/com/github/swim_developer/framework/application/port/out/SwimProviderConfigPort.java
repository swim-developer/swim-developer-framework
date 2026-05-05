package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;

import java.util.Map;
import java.util.Optional;

public interface SwimProviderConfigPort {

    Optional<ProviderConfiguration> findByProviderIdOrDefault(String providerId);

    Optional<ProviderConfiguration> findByProviderId(String providerId);

    Map<String, ProviderConfiguration> getProviderMap();
}
