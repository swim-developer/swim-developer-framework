package com.github.swim_developer.framework.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionHeartbeat(
    UUID subscriptionId,
    String subscriptionState,
    String providerStatus,
    Instant publicationTime,
    Instant nextPublicationTime,
    long sequenceNumber
) {}
