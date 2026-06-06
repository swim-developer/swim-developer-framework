package com.github.swim_developer.framework.integration.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class TestContainers {

    private static final RedpandaContainer REDPANDA;
    private static final MongoDBContainer MONGO;
    private static final GenericContainer<?> ARTEMIS;

    static {
        REDPANDA = new RedpandaContainer(
                DockerImageName.parse("redpandadata/redpanda:v26.1.6")
        );

        MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

        ARTEMIS = new GenericContainer<>(
                DockerImageName.parse("apache/activemq-artemis:2.44.0")
        )
                .withExposedPorts(5672, 8161, 61616)
                .withEnv("ARTEMIS_USER", "admin")
                .withEnv("ARTEMIS_PASSWORD", "admin")
                .withEnv("ANONYMOUS_LOGIN", "true")
                .withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --nio")
                .waitingFor(Wait.forLogMessage(".*AMQ221001.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(120)));

        REDPANDA.start();
        MONGO.start();
        ARTEMIS.start();
    }

    private TestContainers() {
    }

    public static RedpandaContainer redpanda() {
        return REDPANDA;
    }

    public static MongoDBContainer mongo() {
        return MONGO;
    }

    public static GenericContainer<?> artemis() {
        return ARTEMIS;
    }
}
