package com.github.swim_developer.framework.domain.model;


import java.util.UUID;

public record ActiveSubscriptionInfo(
    UUID subscriptionId,
    String queueName,
    String status
) {}
