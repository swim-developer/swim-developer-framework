package com.github.swim_developer.framework.persistence.mongodb;

import com.github.swim_developer.framework.consumer.application.messaging.outbox.OutboxRouterFanOut;
import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.AbstractDeadLetterService;
import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterParams;
import com.github.swim_developer.framework.persistence.mongodb.document.DeadLetterDocument;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class MongoDeadLetterService extends AbstractDeadLetterService {

    private final MongoDeadLetterRepository dlqRepository;
    private final OutboxRouterFanOut outboxRouterFanOut;
    private final String serviceName;

    protected MongoDeadLetterService() {
        this(null, null, null, "swim");
    }

    @Inject
    public MongoDeadLetterService(MeterRegistry meterRegistry,
                                  MongoDeadLetterRepository dlqRepository,
                                  OutboxRouterFanOut outboxRouterFanOut,
                                  @ConfigProperty(name = "swim.service.name", defaultValue = "swim") String serviceName) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.outboxRouterFanOut = outboxRouterFanOut;
        this.serviceName = serviceName;
    }

    @Override
    protected OutboxRouterFanOut getRouterFanOut() {
        return outboxRouterFanOut;
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected void persist(DeadLetterParams params) {
        DeadLetterDocument doc = new DeadLetterDocument();
        doc.setAmqpMessageId(params.amqpMessageId());
        doc.setMessageIndex(params.messageIndex());
        doc.setSubscriptionId(params.subscriptionId());
        doc.setQueueName(params.queueName());
        doc.setRawPayload(params.rawPayload());
        doc.setErrorType(params.errorType());
        doc.setErrorMessage(params.errorMessage());
        doc.setStackTrace(params.stackTrace());
        doc.setReceivedAt(params.receivedAt());
        doc.setFailedAt(params.failedAt());
        dlqRepository.persist(doc);
    }
}
