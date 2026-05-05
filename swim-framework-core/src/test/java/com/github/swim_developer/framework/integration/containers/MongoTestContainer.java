package com.github.swim_developer.framework.integration.containers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.util.Map;

public final class MongoTestContainer {

    private MongoTestContainer() {
    }

    public static String getConnectionString() {
        return TestContainers.mongo().getConnectionString();
    }

    public static Map<String, String> getQuarkusConfig() {
        return Map.of(
                "quarkus.mongodb.connection-string", getConnectionString()
        );
    }

    public static void dropDatabase(String databaseName) {
        try (MongoClient client = MongoClients.create(getConnectionString())) {
            client.getDatabase(databaseName).drop();
        }
    }

    public static void dropAllDatabases() {
        try (MongoClient client = MongoClients.create(getConnectionString())) {
            client.listDatabaseNames().forEach(dbName -> {
                if (!dbName.equals("admin") && !dbName.equals("config") && !dbName.equals("local")) {
                    client.getDatabase(dbName).drop();
                }
            });
        }
    }
}
