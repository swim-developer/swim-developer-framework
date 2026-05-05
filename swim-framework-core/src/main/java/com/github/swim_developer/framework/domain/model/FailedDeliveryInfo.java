package com.github.swim_developer.framework.domain.model;


import java.util.UUID;

public record FailedDeliveryInfo(UUID subscriptionId, String queue, String errorMessage) {
}
