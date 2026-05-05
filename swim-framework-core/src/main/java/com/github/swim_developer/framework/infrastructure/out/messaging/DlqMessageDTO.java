package com.github.swim_developer.framework.infrastructure.out.messaging;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record DlqMessageDTO(
        String id,
        String amqpMessageId,
        int messageIndex,
        String subscriptionId,
        String queueName,
        String errorType,
        String errorMessage,
        String rawPayload,
        Instant receivedAt,
        Instant failedAt
) {
}
