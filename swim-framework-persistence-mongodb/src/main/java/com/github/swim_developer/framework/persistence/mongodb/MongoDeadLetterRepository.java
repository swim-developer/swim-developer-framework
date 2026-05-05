package com.github.swim_developer.framework.persistence.mongodb;

import com.github.swim_developer.framework.persistence.mongodb.document.DeadLetterDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MongoDeadLetterRepository implements PanacheMongoRepository<DeadLetterDocument> {
}
