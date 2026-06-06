package com.github.swim_developer.framework.consumer.application.port.in;

public interface SwimQueryFeaturesPort {

    String queryFeatures(String typeName, String filter, String validTime, String providerId);
}
