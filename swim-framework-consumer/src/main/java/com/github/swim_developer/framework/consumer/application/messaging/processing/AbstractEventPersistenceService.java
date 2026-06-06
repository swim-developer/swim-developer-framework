package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.OutboxDeliveryStatus;
import com.github.swim_developer.framework.application.model.PreparedEvent;
import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxRouterFanOut;
import com.github.swim_developer.framework.domain.exception.ConsumerPersistenceException;
import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class AbstractEventPersistenceService<E, O extends SwimOutboxEvent>
        implements SwimEventPersister<E> {

    protected final OutboxRouterFanOut outboxRouterFanOut;
    protected final SwimDeadLetterPort deadLetterService;

    protected AbstractEventPersistenceService() {
        this.outboxRouterFanOut = null;
        this.deadLetterService = null;
    }

    protected AbstractEventPersistenceService(OutboxRouterFanOut outboxRouterFanOut,
                                              SwimDeadLetterPort deadLetterService) {
        this.outboxRouterFanOut = outboxRouterFanOut;
        this.deadLetterService = deadLetterService;
    }

    protected abstract O assembleEntity(ProcessingContext ctx, E event, String contentHash);

    protected abstract void persistEntity(O entity);

    protected abstract void persistEntities(List<O> entities);

    protected abstract void updateEntity(O entity);

    protected abstract String getServicePrefix();

    @Override
    public void persistAndDispatch(ProcessingContext ctx, E event, String contentHash) {
        O entity = assembleEntity(ctx, event, contentHash);

        try {
            persistEntity(entity);
            log.debug("{} event persisted - MessageId: {}", getServicePrefix(), entity.getMessageId());
        } catch (RuntimeException e) {
            log.error("Failed to persist {} event for message #{}", getServicePrefix(), ctx.index(), e);
            deadLetterService.sendToDeadLetterQueue(
                    ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                    ctx.index(), ctx.xmlPayload(), "PERSISTENCE_ERROR", e);
            throw new ConsumerPersistenceException("Failed to persist " + getServicePrefix() + " event", e);
        }

        dispatchToOutbox(entity);
    }

    @Override
    public void batchPersistAndDispatch(List<PreparedEvent<E>> batch) {
        List<O> entities = batch.stream()
                .map(item -> assembleEntity(item.ctx(), item.event(), item.contentHash()))
                .toList();

        persistEntities(entities);

        log.debug("{} batch persisted - {} events via insertMany", getServicePrefix(), entities.size());

        for (O entity : entities) {
            dispatchToOutbox(entity);
        }
    }

    private void dispatchToOutbox(O entity) {
        try {
            outboxRouterFanOut.route(entity.getMessageId(), entity.getRawPayload());
        } catch (RuntimeException e) {
            log.warn("Outbox fan-out failed, reverting to PENDING for retry - MessageId: {}", entity.getMessageId(), e);
            entity.setDeliveryStatus(OutboxDeliveryStatus.PENDING);
            entity.setDispatchedAt(null);
            updateEntity(entity);
        }
    }
}
