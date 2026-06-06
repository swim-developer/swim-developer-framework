package com.github.swim_developer.framework.persistence.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractMongoIndexInitializer {

    private final MongoClient mongoClient;

    @Inject
    protected AbstractMongoIndexInitializer(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    protected abstract String getDatabaseName();

    protected abstract void defineIndexes(MongoDatabase database);

    public void onStart() {
        log.info("Initializing MongoDB indexes...");
        try {
            MongoDatabase database = mongoClient.getDatabase(getDatabaseName());
            defineIndexes(database);
            log.info("MongoDB indexes initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize MongoDB indexes", e);
        }
    }

    protected void createIndex(MongoCollection<Document> collection, String indexName, Bson keys, IndexOptions options) {
        try {
            for (Document idx : collection.listIndexes()) {
                if (indexName.equals(idx.getString("name"))) {
                    log.debug("Index already exists: {}", indexName);
                    return;
                }
            }
            IndexOptions opts = options != null ? options : new IndexOptions();
            opts.name(indexName);
            collection.createIndex(keys, opts);
            log.info("Created index: {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to create index {}: {}", indexName, e.getMessage());
        }
    }

    protected IndexOptions ttlOptions(int ttlDays) {
        long ttlSeconds = TimeUnit.DAYS.toSeconds(ttlDays);
        return new IndexOptions().expireAfter(ttlSeconds, TimeUnit.SECONDS);
    }
}
