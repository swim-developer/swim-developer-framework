package com.github.swim_developer.framework.persistence.mongodb.document;

import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterMessage;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.time.Instant;

@RegisterForReflection
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MongoEntity(collection = "dead_letter_queue")
public class DeadLetterDocument {

    private ObjectId id;
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

    public DeadLetterMessage toDomain() {
        DeadLetterMessage message = new DeadLetterMessage();
        message.setId(id != null ? id.toHexString() : null);
        message.setAmqpMessageId(amqpMessageId);
        message.setMessageIndex(messageIndex);
        message.setSubscriptionId(subscriptionId);
        message.setQueueName(queueName);
        message.setRawPayload(rawPayload);
        message.setErrorType(errorType);
        message.setErrorMessage(errorMessage);
        message.setStackTrace(stackTrace);
        message.setReceivedAt(receivedAt);
        message.setFailedAt(failedAt);
        return message;
    }
}
