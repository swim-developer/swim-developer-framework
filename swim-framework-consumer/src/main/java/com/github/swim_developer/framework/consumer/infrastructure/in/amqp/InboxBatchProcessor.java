package com.github.swim_developer.framework.consumer.infrastructure.in.amqp;

import com.github.swim_developer.framework.application.port.out.SwimInboxStore;
import com.github.swim_developer.framework.infrastructure.out.messaging.InboxEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.amqp.AmqpMessage;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class InboxBatchProcessor {

    private final Vertx vertx;
    private final MeterRegistry meterRegistry;
    private final SwimInboxStore inboxStore;
    private final String metricPrefix;
    private final int batchSize;
    private final long batchFlushIntervalMs;

    private final ConcurrentLinkedQueue<PendingInbox> inboxBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private Long flushTimerId;

    private Counter eventsReceivedCounter;
    private Counter inboxPublishedCounter;

    public InboxBatchProcessor(Vertx vertx, MeterRegistry meterRegistry,
                               SwimInboxStore inboxStore,
                               String metricPrefix, int batchSize, long batchFlushIntervalMs) {
        this.vertx = vertx;
        this.meterRegistry = meterRegistry;
        this.inboxStore = inboxStore;
        this.metricPrefix = metricPrefix;
        this.batchSize = batchSize;
        this.batchFlushIntervalMs = batchFlushIntervalMs;
    }

    public void init() {
        eventsReceivedCounter = Counter.builder(metricPrefix + "_events_received_total")
            .description("Total events received from provider via AMQP")
            .register(meterRegistry);
        inboxPublishedCounter = Counter.builder(metricPrefix + "_inbox_published_total")
            .description("Total messages stored to inbox")
            .register(meterRegistry);

        flushTimerId = vertx.setPeriodic(batchFlushIntervalMs, id -> {
            if (pendingCount.get() > 0) {
                vertx.executeBlocking(() -> {
                    flushBatch();
                    return null;
                }, false);
            }
        });
        log.info("Inbox batch processor enabled - batchSize: {}, flushIntervalMs: {}", batchSize, batchFlushIntervalMs);
    }

    public void enqueue(InboxEnvelope envelope, AmqpMessage message) {
        eventsReceivedCounter.increment();
        inboxBuffer.offer(new PendingInbox(envelope, message));

        if (pendingCount.incrementAndGet() >= batchSize) {
            vertx.executeBlocking(() -> {
                flushBatch();
                return null;
            }, false);
        }
    }

    public void flushBatch() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<PendingInbox> batch = new ArrayList<>(batchSize + 16);
            PendingInbox pending;
            while ((pending = inboxBuffer.poll()) != null) {
                batch.add(pending);
            }
            pendingCount.addAndGet(-batch.size());
            if (batch.isEmpty()) {
                return;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>(batch.size());
            for (PendingInbox p : batch) {
                CompletableFuture<Void> future = inboxStore.store(p.envelope()).toCompletableFuture();
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            for (PendingInbox p : batch) {
                p.message().accepted();
            }
            inboxPublishedCounter.increment(batch.size());
            log.debug("Inbox batch stored and AMQP ACKed - size: {}", batch.size());
        } catch (Exception e) {
            log.error("Inbox batch failed - rejecting all AMQP messages", e);
            PendingInbox remaining;
            while ((remaining = inboxBuffer.poll()) != null) {
                remaining.message().rejected();
                pendingCount.decrementAndGet();
            }
        } finally {
            flushing.set(false);
        }
    }

    public void cleanup() {
        if (flushTimerId != null) {
            vertx.cancelTimer(flushTimerId);
        }
        flushBatch();
    }
}
