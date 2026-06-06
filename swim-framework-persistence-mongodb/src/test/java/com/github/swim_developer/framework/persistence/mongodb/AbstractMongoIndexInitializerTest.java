package com.github.swim_developer.framework.persistence.mongodb;

import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class AbstractMongoIndexInitializerTest {

    private MongoClient mongoClient;
    private MongoDatabase database;

    @BeforeEach
    void setUp() {
        mongoClient = mock(MongoClient.class);
        database = mock(MongoDatabase.class);
        when(mongoClient.getDatabase("swim-db")).thenReturn(database);
    }

    private AbstractMongoIndexInitializer initializer(Runnable defineAction) {
        return new AbstractMongoIndexInitializer(mongoClient) {
            @Override
            protected String getDatabaseName() { return "swim-db"; }
            @Override
            protected void defineIndexes(MongoDatabase db) { defineAction.run(); }
        };
    }

    @Test
    void onStart_callsDefineIndexes() {
        boolean[] called = {false};
        initializer(() -> called[0] = true).onStart();
        assertThat(called[0]).isTrue();
    }

    @Test
    void onStart_doesNotThrow_whenDefineIndexesFails() {
        AbstractMongoIndexInitializer init = initializer(() -> { throw new RuntimeException("boom"); });
        org.assertj.core.api.Assertions.assertThatCode(init::onStart).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIndex_createsIndex_whenNotExists() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iterable.iterator()).thenReturn(cursor);
        when(collection.listIndexes()).thenReturn(iterable);

        Bson keys = new Document("field", 1);
        initializer(() -> {}).createIndex(collection, "my-index", keys, null);

        verify(collection).createIndex(eq(keys), any(IndexOptions.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIndex_skipsCreation_whenIndexAlreadyExists() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        Document existingIdx = new Document("name", "my-index");
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(existingIdx);
        when(iterable.iterator()).thenReturn(cursor);
        when(collection.listIndexes()).thenReturn(iterable);

        initializer(() -> {}).createIndex(collection, "my-index", new Document("field", 1), null);

        verify(collection, never()).createIndex(any(Bson.class), any(IndexOptions.class));
    }

    @Test
    void ttlOptions_returnsOptionsWithCorrectExpiry() {
        IndexOptions opts = initializer(() -> {}).ttlOptions(7);
        assertThat(opts.getExpireAfter(TimeUnit.SECONDS)).isEqualTo(TimeUnit.DAYS.toSeconds(7));
    }
}
