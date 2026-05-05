package com.github.swim_developer.framework.integration.containers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DeleteTopicsResult;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class RedpandaTestContainer {

    private RedpandaTestContainer() {
    }

    public static String getBootstrapServers() {
        return TestContainers.redpanda().getBootstrapServers();
    }

    public static Map<String, String> getQuarkusConfig() {
        return Map.of(
                "kafka.BOOTSTRAP_SERVERS_KEY", getBootstrapServers()
        );
    }

    public static void deleteTopics(String... topicNames) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            DeleteTopicsResult result = adminClient.deleteTopics(Set.of(topicNames));
            result.all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete topics", e);
        }
    }

    public static void cleanupAllTopics() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            Set<String> topics = adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            if (!topics.isEmpty()) {
                adminClient.deleteTopics(topics).all().get(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to cleanup topics", e);
        }
    }
}
