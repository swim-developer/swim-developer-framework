package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterMessage;

import java.util.List;

public interface DeadLetterStore {

    void persist(DeadLetterMessage message);

    List<DeadLetterMessage> listAllDomain();

    List<DeadLetterMessage> findAllPaginated(int page, int size);

    long countAll();

    void deleteAll();
}
