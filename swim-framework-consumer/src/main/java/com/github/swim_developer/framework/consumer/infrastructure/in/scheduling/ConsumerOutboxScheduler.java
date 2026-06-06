package com.github.swim_developer.framework.consumer.infrastructure.in.scheduling;

import com.github.swim_developer.framework.application.model.OutboxDeliveryStatus;
import com.github.swim_developer.framework.application.service.AbstractOutboxScheduler;
import com.github.swim_developer.framework.consumer.application.port.out.SwimOutboxEventStorePort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimOutboxRetryPort;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Slf4j
@ApplicationScoped
public class ConsumerOutboxScheduler extends AbstractOutboxScheduler<SwimOutboxEvent> {

    private final Instance<SwimOutboxEventStorePort> eventStores;
    private final Instance<SwimOutboxRetryPort> retryPorts;
    private final Instance<SwimDeadLetterPort> dlqServices;
    private final boolean persistRetryCount;

    protected ConsumerOutboxScheduler() {
        this.eventStores = null;
        this.retryPorts = null;
        this.dlqServices = null;
        this.persistRetryCount = true;
    }

    @Inject
    public ConsumerOutboxScheduler(
            LeaderElection leaderElection,
            Instance<SwimOutboxEventStorePort> eventStores,
            Instance<SwimOutboxRetryPort> retryPorts,
            Instance<SwimDeadLetterPort> dlqServices,
            @ConfigProperty(name = "swim.outbox.max-retries", defaultValue = "3") int maxRetries,
            @ConfigProperty(name = "swim.outbox.recovery.batch-size", defaultValue = "100") int recoveryBatchSize,
            @ConfigProperty(name = "swim.outbox.persist-retry-count", defaultValue = "true") boolean persistRetryCount) {
        super(leaderElection, maxRetries, recoveryBatchSize);
        this.eventStores = eventStores;
        this.retryPorts = retryPorts;
        this.dlqServices = dlqServices;
        this.persistRetryCount = persistRetryCount;
    }

    @Scheduled(every = "${swim.outbox.recovery.interval:30s}", delayed = "${swim.scheduler.initial-delay:30s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void retryPendingOutboxEvents() {
        recoverOrphanedEvents();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<SwimOutboxEvent> findPendingEvents(int batchSize) {
        return (List<SwimOutboxEvent>) eventStores.get().findPendingOutboxEvents(batchSize);
    }

    @Override
    protected boolean exceedsMaxRetries(SwimOutboxEvent event) {
        return event.getOutboxRetryCount() >= getMaxRetries();
    }

    @Override
    protected void handleMaxRetriesExceeded(SwimOutboxEvent event) {
        dlqServices.get().sendMessageToDeadLetterQueue(
                event.getMessageId(), event.getSubscriptionId(),
                event.getQueueName(), event.getRawPayload(),
                event.getReceivedAt(), "KAFKA_MAX_RETRIES_EXCEEDED");
        event.setDeliveryStatus(OutboxDeliveryStatus.FAILED);
        eventStores.get().updateOutboxEvent(event);
    }

    @Override
    protected void redispatch(SwimOutboxEvent event) {
        if (persistRetryCount) {
            event.setOutboxRetryCount(event.getOutboxRetryCount() + 1);
            eventStores.get().updateOutboxEvent(event);
        }
        retryPorts.get().retryOutboxEvent(event);
    }
}
