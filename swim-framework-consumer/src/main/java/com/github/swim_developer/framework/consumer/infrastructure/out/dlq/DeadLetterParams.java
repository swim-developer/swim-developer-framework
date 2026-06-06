package com.github.swim_developer.framework.consumer.infrastructure.out.dlq;

import java.time.Instant;

public record DeadLetterParams(
        String amqpMessageId,
        int messageIndex,
        String subscriptionId,
        String queueName,
        String rawPayload,
        String errorType,
        String errorMessage,
        String stackTrace,
        Instant receivedAt,
        Instant failedAt) {
}
