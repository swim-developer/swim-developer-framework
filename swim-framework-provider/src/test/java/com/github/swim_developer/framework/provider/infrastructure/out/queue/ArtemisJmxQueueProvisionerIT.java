package com.github.swim_developer.framework.provider.infrastructure.out.queue;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ExtendWith(TestNameLoggerExtension.class)
@Slf4j
class AmqpQueueProvisionerIT {

    @Container
    static GenericContainer<?> artemis = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.44.0"))
            .withExposedPorts(8161, 5672, 61616)
            .withEnv("ARTEMIS_USER", "admin")
            .withEnv("ARTEMIS_PASSWORD", "admin")
            .withEnv("ANONYMOUS_LOGIN", "true")
            .withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --nio")
            .waitingFor(Wait.forLogMessage(".*AMQ221001.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private AmqpQueueProvisioner provisioner;
    private HttpClient httpClient;
    private String jolokiaUrl;
    private String authHeader;

    @BeforeEach
    void setUp() {
        String host = artemis.getHost();
        Integer port = artemis.getMappedPort(8161);
        jolokiaUrl = String.format("http://%s:%d/console/jolokia", host, port);

        provisioner = provisionerFor(jolokiaUrl, "admin", "admin", "0.0.0.0");

        httpClient = HttpClient.newHttpClient();
        authHeader = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        for (String q : new String[]{"test.queue.1", "test.queue.2", "DNOTAM.user1.test"}) {
            try {
                provisioner.removeQueue(q);
            } catch (Exception ignored) {
                // Best-effort teardown: queue may already be absent on the broker.
            }
        }
    }

    @Test
    void createQueueSuccessfully() throws Exception {
        String queueName = "test.queue.1";

        provisioner.createQueue(queueName);

        assertThat(checkQueueExists(queueName)).isTrue();
    }

    @Test
    void createQueueIsIdempotent() {
        String queueName = "test.queue.2";

        provisioner.createQueue(queueName);

        assertThatThrownBy(() -> provisioner.createQueue(queueName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Jolokia createQueue failed");
    }

    @Test
    void addSecurityRoleSuccessfully() throws Exception {
        String queueName = "DNOTAM.user1.test";

        provisioner.createQueue(queueName);
        provisioner.addSecurityRole(queueName, "user1", "-amq");

        assertThat(checkSecurityRoleExists(queueName, "user1-amq")).isTrue();
    }

    @Test
    void removeQueueSuccessfully() throws Exception {
        String queueName = "test.queue.1";

        provisioner.createQueue(queueName);
        assertThat(checkQueueExists(queueName)).isTrue();

        provisioner.removeQueue(queueName);

        assertThat(checkQueueExists(queueName)).isFalse();
    }

    @Test
    void removeQueueIsIdempotent() {
        assertThatCode(() -> provisioner.removeQueue("nonexistent.queue"))
                .doesNotThrowAnyException();
    }

    @Test
    void removeSecurityRoleSuccessfully() throws Exception {
        String queueName = "DNOTAM.user1.test";

        provisioner.createQueue(queueName);
        provisioner.addSecurityRole(queueName, "user1", "-amq");

        provisioner.removeSecurityRole(queueName);

        assertThat(checkSecurityRoleExists(queueName, "user1-amq")).isFalse();
    }

    @Test
    void removeSecurityRoleIsIdempotent() {
        assertThatCode(() -> provisioner.removeSecurityRole("nonexistent.queue"))
                .doesNotThrowAnyException();
    }

    @Test
    void createQueueWithInvalidJolokiaUrlFails() {
        AmqpQueueProvisioner badProvisioner = provisionerFor(
                "http://invalid-host:9999/jolokia", "admin", "admin", "http://invalid-host:9999");

        assertThatThrownBy(() -> badProvisioner.createQueue("test.queue"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Jolokia createQueue failed");
    }

    private static AmqpQueueProvisioner provisionerFor(String url, String user, String password, String brokerName) {
        AmqpQueueProvisioner p = new AmqpQueueProvisioner();
        p.jolokiaUrl = url;
        p.user = user;
        p.password = password;
        p.brokerName = brokerName;
        return p;
    }

    private boolean checkQueueExists(String queueName) throws Exception {
        String payload = """
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"0.0.0.0\\"",
              "operation": "getQueueNames(java.lang.String)",
              "arguments": ["ANYCAST"]
            }
            """;

        HttpResponse<String> response = doJolokia(payload);
        return response.body().contains(queueName);
    }

    private boolean checkSecurityRoleExists(String address, String roleName) throws Exception {
        String payload = """
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"0.0.0.0\\"",
              "operation": "getRolesAsJSON(java.lang.String)",
              "arguments": ["%s"]
            }
            """.formatted(address);

        HttpResponse<String> response = doJolokia(payload);
        return response.body().contains(roleName);
    }

    private HttpResponse<String> doJolokia(String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jolokiaUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Jolokia call failed: " + response.body());
        }
        return response;
    }
}
