package com.github.swim_developer.framework.infrastructure.out.messaging;

import com.github.swim_developer.framework.application.port.out.SwimOutboxRouter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.kafka.Record;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractOutboxRouter implements SwimOutboxRouter {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> processedCounters = new ConcurrentHashMap<>();

    protected AbstractOutboxRouter() {
        this.meterRegistry = null;
    }

    protected AbstractOutboxRouter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void sendToDeadLetterQueue(String messageId, String payload) {
        getDlqEmitter().send(Record.of(messageId, payload));
        log.warn("{} event sent to DLQ - MessageId: {}", getServiceLabel(), messageId);
    }

    protected void incrementCounter(String metricName,
                                    String tag1Name, String tag1Value,
                                    String tag2Name, String tag2Value) {
        String key = tag1Value + ":" + tag2Value;
        processedCounters.computeIfAbsent(key, k ->
                Counter.builder(metricName)
                        .tag(tag1Name, tag1Value)
                        .tag(tag2Name, tag2Value)
                        .register(meterRegistry))
                .increment();
    }

    protected abstract Emitter<Record<String, String>> getDlqEmitter();

    protected abstract String getServiceLabel();
}
