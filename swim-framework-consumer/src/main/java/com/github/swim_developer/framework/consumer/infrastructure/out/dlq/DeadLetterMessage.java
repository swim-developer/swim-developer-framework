package com.github.swim_developer.framework.consumer.infrastructure.out.dlq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessage {

    private String id;
    private String amqpMessageId;
    private Integer messageIndex;
    private String subscriptionId;
    private String queueName;
    private String rawPayload;
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private Instant receivedAt;
    private Instant failedAt;
}
