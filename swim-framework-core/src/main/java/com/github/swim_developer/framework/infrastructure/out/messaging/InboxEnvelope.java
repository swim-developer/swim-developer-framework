package com.github.swim_developer.framework.infrastructure.out.messaging;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record InboxEnvelope(
        String subscriptionId,
        String queueName,
        String amqpMessageId,
        String rawPayload,
        String contentType,
        Instant receivedAt) {
}
