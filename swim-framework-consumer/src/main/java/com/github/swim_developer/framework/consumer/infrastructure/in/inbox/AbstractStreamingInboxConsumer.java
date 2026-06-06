package com.github.swim_developer.framework.consumer.infrastructure.in.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.framework.application.port.in.SwimInboxReader;
import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class AbstractStreamingInboxConsumer implements SwimInboxReader {

    protected final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    protected Counter processedCounter;
    protected Counter failedCounter;

    @Inject
    protected AbstractStreamingInboxConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        String prefix = getMetricPrefix();
        processedCounter = Counter.builder(prefix + "_inbox_processed_total")
                .description("Total messages processed from inbox stream")
                .register(meterRegistry);
        failedCounter = Counter.builder(prefix + "_inbox_failed_total")
                .description("Total messages failed from inbox stream (sent to DLQ)")
                .register(meterRegistry);
    }

    protected CompletionStage<Void> consume(Message<String> message) {
        InboxEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayload(), InboxEnvelope.class);
        } catch (Exception e) {
            log.error("Failed to deserialize InboxEnvelope - sending to DLQ", e);
            failedCounter.increment();
            return message.ack();
        }

        try {
            List<String> messages = extractMessages(envelope.rawPayload());

            if (messages.size() == 1) {
                processSingleMessage(envelope, messages.getFirst(), 0);
            } else {
                processMessagesInParallel(envelope, messages);
            }

            processedCounter.increment();
        } catch (Exception e) {
            log.error("Inbox processing failed - MessageId: {}, Queue: {}, SubId: {}",
                    envelope.amqpMessageId(), envelope.queueName(), envelope.subscriptionId(), e);
            failedCounter.increment();
        }

        return message.ack();
    }

    private void processMessagesInParallel(InboxEnvelope envelope, List<String> messages) {
        AtomicInteger failed = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            final int index = i;
            final String msg = messages.get(i);
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    processSingleMessage(envelope, msg, index);
                } catch (Exception e) {
                    log.error("Failed to process message #{} - MessageId: {}", index, envelope.amqpMessageId(), e);
                    failed.incrementAndGet();
                }
            });
        }
        CompletableFuture.allOf(futures).join();

        if (failed.get() > 0) {
            log.warn("Inbox batch had {} failures out of {} messages - MessageId: {}",
                    failed.get(), messages.size(), envelope.amqpMessageId());
        }
    }

    @Override
    public List<String> extractMessages(String rawPayload) {
        return List.of(rawPayload);
    }

}
