package com.github.swim_developer.framework.consumer.infrastructure.out.dlq;

import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxRouterFanOut;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractDeadLetterService implements com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort {

    protected final MeterRegistry meterRegistry;
    private final Map<String, Counter> dlqCounters = new ConcurrentHashMap<>();

    @Inject
    protected AbstractDeadLetterService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    protected abstract OutboxRouterFanOut getRouterFanOut();

    protected abstract String getServiceName();

    protected abstract void persist(DeadLetterParams params);

    protected void persistDeadLetter(DeadLetterParams params) {
        persist(params);
    }

    @PostConstruct
    void initDlqMetrics() {
        String prefix = getServiceName();
        String[] errorTypes = {"VALIDATION_ERROR", "PERSISTENCE_ERROR", "VALIDATOR_INIT_ERROR",
                "EXTRACTION_ERROR", "UNKNOWN_ERROR", "KAFKA_MAX_RETRIES_EXCEEDED",
                "INBOX_MAX_RETRIES_EXCEEDED"};
        for (String errorType : errorTypes) {
            dlqCounters.put(errorType, Counter.builder(prefix + "_events_dlq_total")
                    .tag("error_type", errorType)
                    .register(meterRegistry));
        }
    }

    protected void incrementDlqCounter(String errorType) {
        Counter counter = dlqCounters.get(errorType);
        if (counter == null) {
            counter = dlqCounters.get("UNKNOWN_ERROR");
        }
        if (counter != null) {
            counter.increment();
        }
    }

    public void sendToDeadLetterQueue(String subscriptionId, String queueName,
                                       String amqpMessageId, int index, String payload,
                                       String errorType, Exception exception) {
        try {
            incrementDlqCounter(errorType);
            Instant now = Instant.now();
            DeadLetterParams params = new DeadLetterParams(
                    amqpMessageId, index, subscriptionId, queueName, payload,
                    errorType, exception.getMessage(), getStackTraceAsString(exception),
                    now, now
            );
            persistDeadLetter(params);
            getRouterFanOut().sendToDeadLetterQueue(amqpMessageId + "#" + index, payload);
            log.warn("DLQ: Message sent - messageId={}#{}, errorType={}", amqpMessageId, index, errorType);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    public void sendMessageToDeadLetterQueue(String amqpMessageId, String subscriptionId,
                                              String queueName, String rawPayload,
                                              Instant receivedAt, String errorType) {
        try {
            incrementDlqCounter(errorType);
            DeadLetterParams params = new DeadLetterParams(
                    amqpMessageId, 0, subscriptionId, queueName, rawPayload,
                    errorType, errorType, "", receivedAt, Instant.now()
            );
            persistDeadLetter(params);
            getRouterFanOut().sendToDeadLetterQueue(amqpMessageId, rawPayload);
            log.warn("DLQ: Message sent - messageId={}, errorType={}", amqpMessageId, errorType);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    public void sendEventToDeadLetterQueue(String messageId, String rawPayload, String errorType) {
        try {
            incrementDlqCounter(errorType);
            getRouterFanOut().sendToDeadLetterQueue(messageId, rawPayload);
            log.warn("DLQ: Event sent to DLQ - MessageId: {}, ErrorType: {}", messageId, errorType);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send event to DLQ - MessageId: {}", messageId, e);
        }
    }

    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
