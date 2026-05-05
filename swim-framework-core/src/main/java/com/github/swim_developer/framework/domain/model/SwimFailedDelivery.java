package com.github.swim_developer.framework.domain.model;

import java.time.Instant;
import java.util.UUID;

public interface SwimFailedDelivery {

    String getEventId();

    UUID getSubscriptionId();

    String getQueue();

    String getErrorMessage();

    void setErrorMessage(String msg);

    int getRetryCount();

    void setRetryCount(int count);

    boolean isResolved();

    void setResolved(boolean resolved);

    void setResolvedAt(Instant resolvedAt);
}
