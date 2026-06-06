package com.github.swim_developer.framework.application.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ProcessingContext(
        String subscriptionId,
        String queueName,
        String amqpMessageId,
        String xmlPayload,
        int index,
        String inboxId) {

    public String compositeMessageId() {
        return amqpMessageId + "#" + index;
    }
}
