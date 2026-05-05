package com.github.swim_developer.framework.provider.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimProviderEvent;

import java.util.List;

public interface SwimProviderEventStorePort {

    @SuppressWarnings("java:S1452")
    List<? extends SwimProviderEvent> findPendingDelivery(int batchSize);

    SwimProviderEvent findDomainById(String eventId);
}
