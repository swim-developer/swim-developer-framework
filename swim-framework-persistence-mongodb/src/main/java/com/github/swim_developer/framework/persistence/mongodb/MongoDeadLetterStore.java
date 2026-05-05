package com.github.swim_developer.framework.persistence.mongodb;

import com.github.swim_developer.framework.consumer.application.port.out.DeadLetterStore;
import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterMessage;
import com.github.swim_developer.framework.persistence.mongodb.document.DeadLetterDocument;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class MongoDeadLetterStore implements DeadLetterStore {

    private final MongoDeadLetterRepository repository;

    @Inject
    public MongoDeadLetterStore(MongoDeadLetterRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DeadLetterMessage> listAllDomain() {
        return repository.listAll().stream().map(DeadLetterDocument::toDomain).toList();
    }

    @Override
    public List<DeadLetterMessage> findAllPaginated(int page, int size) {
        return repository.findAll(Sort.descending("failedAt"))
                .page(Page.of(page, size))
                .list().stream().map(DeadLetterDocument::toDomain).toList();
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public void persist(DeadLetterMessage message) {
        DeadLetterDocument doc = new DeadLetterDocument();
        doc.setAmqpMessageId(message.getAmqpMessageId());
        doc.setMessageIndex(message.getMessageIndex());
        doc.setSubscriptionId(message.getSubscriptionId());
        doc.setQueueName(message.getQueueName());
        doc.setRawPayload(message.getRawPayload());
        doc.setErrorType(message.getErrorType());
        doc.setErrorMessage(message.getErrorMessage());
        doc.setStackTrace(message.getStackTrace());
        doc.setReceivedAt(message.getReceivedAt());
        doc.setFailedAt(message.getFailedAt());
        repository.persist(doc);
    }
}
