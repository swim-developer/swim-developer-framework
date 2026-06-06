package com.github.swim_developer.framework.integration.containers;

import java.util.Map;

public final class ArtemisTestContainer {

    private ArtemisTestContainer() {
    }

    public static String getHost() {
        return TestContainers.artemis().getHost();
    }

    public static Integer getAmqpPort() {
        return TestContainers.artemis().getMappedPort(5672);
    }

    public static Integer getCorePort() {
        return TestContainers.artemis().getMappedPort(61616);
    }

    public static Integer getConsolePort() {
        return TestContainers.artemis().getMappedPort(8161);
    }

    public static String getAmqpUrl() {
        return String.format("amqp://%s:%d", getHost(), getAmqpPort());
    }

    public static Map<String, String> getQuarkusConfig() {
        return Map.of(
                "amqp-host", getHost(),
                "amqp-port", String.valueOf(getAmqpPort()),
                "amqp-username", "admin",
                "amqp-password", "admin"
        );
    }
}
