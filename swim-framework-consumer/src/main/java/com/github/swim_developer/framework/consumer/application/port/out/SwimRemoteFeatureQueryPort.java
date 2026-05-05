package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;

public interface SwimRemoteFeatureQueryPort {

    String queryFeatures(String typeName, String filter, String validTime, ProviderConfiguration provider);
}
