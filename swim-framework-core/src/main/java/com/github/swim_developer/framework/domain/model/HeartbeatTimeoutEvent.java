package com.github.swim_developer.framework.domain.model;


public record HeartbeatTimeoutEvent(String subscriptionId, SubscriptionHeartbeat lastHeartbeat) {}
