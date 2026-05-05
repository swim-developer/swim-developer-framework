package com.github.swim_developer.framework.infrastructure.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.framework.application.port.out.SwimInboxStore;
import com.github.swim_developer.framework.domain.exception.InboxSerializationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for SwimInboxStore implementations.
 * Provides JSON serialization of InboxEnvelope for transport to the storage backend.
 * AMQ-based implementations may override store() directly without using toJson().
 */
@Slf4j
public abstract class AbstractInboxStore implements SwimInboxStore {

    protected final ObjectMapper objectMapper;

    protected AbstractInboxStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes an InboxEnvelope to JSON.
     * Kafka implementations use this to produce a JSON String record.
     */
    protected String toJson(InboxEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Failed to serialize InboxEnvelope - MessageId: {}", envelope.amqpMessageId(), e);
            throw new InboxSerializationException("Failed to serialize InboxEnvelope", e);
        }
    }

    protected abstract String getStoreLabel();
}
