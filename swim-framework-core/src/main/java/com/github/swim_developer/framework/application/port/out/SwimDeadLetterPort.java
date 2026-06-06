package com.github.swim_developer.framework.application.port.out;

import java.time.Instant;

public interface SwimDeadLetterPort {

    void sendToDeadLetterQueue(String subscriptionId, String queueName,
                               String amqpMessageId, int index, String payload,
                               String errorType, Exception exception);

    void sendMessageToDeadLetterQueue(String amqpMessageId, String subscriptionId,
                                      String queueName, String rawPayload,
                                      Instant receivedAt, String errorType);

    void sendEventToDeadLetterQueue(String messageId, String rawPayload, String errorType);
}
