package com.github.swim_developer.framework.consumer.infrastructure.in.inbox;

import com.github.swim_developer.framework.application.model.ProcessingOutcome;
import com.github.swim_developer.framework.application.model.InboxStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes a single inbox entry end-to-end: payload extraction → per-message
 * processing → status update.
 *
 * <h2>Intended use case</h2>
 * <p>SWIM services whose inbound messages may contain <em>batches</em> of
 * domain events in a single AMQP delivery (e.g., an FF-ICE Publication message
 * containing multiple eFPL updates, or a bulk DNOTAM snapshot). The consumer
 * extracts individual messages from the raw payload, processes them in parallel,
 * and sets a final {@link com.github.swim_developer.framework.application.model.InboxStatus}
 * on the inbox entry reflecting partial or full completion.</p>
 *
 * <h2>Processing flow</h2>
 * <pre>
 * AMQP receive → persist inbox (RECEIVED)
 *             → {@link #process(Object)}
 *                 → extractMessages()        (split batch if needed)
 *                 → processSingleMessage()   (per-item, parallel if >1)
 *                 → updateInboxFinalStatus() (COMPLETED / PARTIAL_FAILED / FAILED)
 * </pre>
 *
 * <h2>Typical extension for FF-ICE</h2>
 * <pre>{@code
 * @ApplicationScoped
 * public class FficeInboxEventConsumer extends AbstractInboxEventConsumer<FficeInboxDocument> {
 *
 *     @Override
 *     protected ProcessingOutcome processSingleMessage(FficeInboxDocument inbox,
 *                                                       String message, int index) {
 *         FficeMessage ffice = unmarshal(message);
 *         flightPlanStore.persist(mapper.toDomain(ffice));
 *         return ProcessingOutcome.PERSISTED;
 *     }
 * }
 * }</pre>
 *
 * @param <I> the inbox entity type
 */
@Slf4j
public abstract class AbstractInboxEventConsumer<I> {

    public void processInboxEvent(String inboxId) {
        I inbox;
        try {
            inbox = resolveInbox(inboxId);
        } catch (Exception e) {
            log.error("Failed to resolve inbox (cache expired?) - InboxId: {}", inboxId);
            return;
        }
        if (inbox == null) {
            log.error("Inbox not found: {}", inboxId);
            return;
        }
        process(inbox);
    }

    public void process(I inbox) {
        try {
            String rawPayload = getRawPayload(inbox);
            List<String> messages = extractMessages(rawPayload);

            int persisted;
            int skipped;
            int failed;

            if (messages.size() == 1) {
                var counts = processSingleMessageDirect(inbox, messages.getFirst(), 0);
                persisted = counts[0];
                skipped = counts[1];
                failed = counts[2];
            } else {
                var counts = processMessagesInParallel(inbox, messages);
                persisted = counts[0];
                skipped = counts[1];
                failed = counts[2];
            }

            InboxStatus finalStatus = determineFinalStatus(persisted, failed);
            updateInboxFinalStatus(inbox, finalStatus, persisted);

            log.debug("Inbox outcome - Persisted: {}/{}, Skipped: {}, Failed: {}, Status: {}",
                    persisted, messages.size(), skipped, failed, finalStatus);
        } catch (Exception e) {
            log.error("Inbox processing error", e);
            updateInboxFinalStatus(inbox, InboxStatus.FAILED, 0);
        }
    }

    private int[] processSingleMessageDirect(I inbox, String message, int index) {
        try {
            ProcessingOutcome outcome = processSingleMessage(inbox, message, index);
            return outcome == ProcessingOutcome.PERSISTED ? new int[]{1, 0, 0} : new int[]{0, 1, 0};
        } catch (Exception e) {
            log.error("Failed to process message #{}", index, e);
            return new int[]{0, 0, 1};
        }
    }

    private int[] processMessagesInParallel(I inbox, List<String> messages) {
        AtomicInteger persisted = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            final int index = i;
            final String message = messages.get(i);
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    ProcessingOutcome outcome = processSingleMessage(inbox, message, index);
                    if (outcome == ProcessingOutcome.PERSISTED) {
                        persisted.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Failed to process message #{}", index, e);
                    failed.incrementAndGet();
                }
            });
        }
        CompletableFuture.allOf(futures).join();
        return new int[]{persisted.get(), skipped.get(), failed.get()};
    }

    private InboxStatus determineFinalStatus(int persisted, int failed) {
        if (failed == 0) return InboxStatus.COMPLETED;
        if (persisted == 0) return InboxStatus.FAILED;
        return InboxStatus.PARTIAL_FAILED;
    }

    protected abstract I resolveInbox(String inboxId);

    protected abstract String getRawPayload(I inbox);

    protected List<String> extractMessages(String rawPayload) {
        return List.of(rawPayload);
    }

    protected abstract ProcessingOutcome processSingleMessage(I inbox, String message, int index);

    protected abstract void updateInboxFinalStatus(I inbox, InboxStatus status, int processedCount);
}
