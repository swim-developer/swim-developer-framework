package com.github.swim_developer.framework.consumer.application.port.out;

public interface SwimPersistenceHealthPort {

    long count();

    String getCollectionName();
}
