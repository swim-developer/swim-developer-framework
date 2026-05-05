package com.github.swim_developer.framework.provider.infrastructure.out.queue;

import com.github.swim_developer.framework.domain.exception.QueueProvisioningException;
import com.github.swim_developer.framework.application.port.out.QueueProvisioningStrategy;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AMQP queue provisioning strategy using Jolokia REST API (JMX over HTTP).
 *
 * <h2>How It Works</h2>
 * <p>
 * This implementation provisions queues by invoking Artemis management operations via Jolokia:
 * </p>
 * <ol>
 *   <li>Creates queue → {@code createQueue()} operation on {@code ActiveMQServerControl} MBean</li>
 *   <li>Adds security → {@code addSecuritySettings()} operation on {@code ActiveMQServerControl} MBean</li>
 *   <li>Changes are applied <strong>immediately</strong> and persisted to the broker bindings journal</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>
 * artemis.jolokia.url=http://localhost:8161/console/jolokia
 * artemis.jolokia.user=admin
 * artemis.jolokia.password=admin
 * artemis.broker.name=amq-broker
 * </pre>
 *
 * <h2>Activation</h2>
 * <p>
 * Active in all profiles <strong>except</strong> {@code prod} via {@code @UnlessBuildProfile("prod")}.
 * </p>
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
@Slf4j
public class AmqpQueueProvisioner implements QueueProvisioningStrategy {

    @ConfigProperty(name = "artemis.jolokia.url", defaultValue = "http://localhost:8161/console/jolokia")
    String jolokiaUrl;

    @ConfigProperty(name = "artemis.jolokia.user", defaultValue = "admin")
    String user;

    @ConfigProperty(name = "artemis.jolokia.password", defaultValue = "admin")
    String password;

    @ConfigProperty(name = "artemis.broker.name", defaultValue = "amq-broker")
    String brokerName;


    @Override
    public void createQueue(String queueName) {
        log.info("Creating queue via Jolokia JMX API: {}", queueName);

        String payload = String.format("""
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"%s\\"",
              "operation": "createQueue(java.lang.String,java.lang.String,java.lang.String,java.lang.String,boolean,int,boolean,boolean)",
              "arguments": ["%s", "ANYCAST", "%s", null, true, -1, false, true]
            }
            """, brokerName, queueName, queueName);

        executeJolokia(payload, "createQueue", queueName);
    }

    @Override
    public void addSecurityRole(String queueName, String username, String roleSuffix) {
        String roleName = username + roleSuffix;
        log.info("Adding security role via Jolokia JMX API - Queue: {}, Role: {}", queueName, roleName);

        String payload = String.format("""
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"%s\\"",
              "operation": "addSecuritySettings(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)",
              "arguments": ["%s", "admin", "%s", "admin", "admin", "admin", "admin", "admin", "%s", "admin", "admin"]
            }
            """, brokerName, queueName, roleName, roleName);

        executeJolokia(payload, "addSecuritySettings", queueName);
    }

    @Override
    public void removeQueue(String queueName) {
        log.info("Removing queue via Jolokia JMX API: {}", queueName);

        String payload = String.format("""
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"%s\\"",
              "operation": "destroyQueue(java.lang.String,boolean,boolean)",
              "arguments": ["%s", true, true]
            }
            """, brokerName, queueName);

        try {
            executeJolokia(payload, "destroyQueue", queueName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.debug("Queue already removed: {}", queueName);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void removeSecurityRole(String queueName) {
        log.info("Removing security settings via Jolokia JMX API: {}", queueName);

        String payload = String.format("""
            {
              "type": "exec",
              "mbean": "org.apache.activemq.artemis:broker=\\"%s\\"",
              "operation": "removeSecuritySettings(java.lang.String)",
              "arguments": ["%s"]
            }
            """, brokerName, queueName);

        try {
            executeJolokia(payload, "removeSecuritySettings", queueName);
        } catch (Exception e) {
            log.debug("Security settings already removed for queue: {}", queueName);
        }
    }

    private void executeJolokia(String jsonPayload, String operation, String queueName) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            String auth = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jolokiaUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                log.error("Jolokia {} failed - Status: {}, Response: {}", operation, response.statusCode(), response.body());
                throw new QueueProvisioningException(String.format("Jolokia %s failed for queue %s: HTTP %d - %s",
                        operation, queueName, response.statusCode(), response.body()));
            }

            String responseBody = response.body();
            if (responseBody.contains("\"status\":4") || responseBody.contains("\"error\"")) {
                log.error("Jolokia {} returned error - Response: {}", operation, responseBody);
                throw new QueueProvisioningException(String.format("Jolokia %s failed for queue %s: %s",
                        operation, queueName, responseBody));
            }

            log.debug("Jolokia {} succeeded for queue: {} - Response: {}", operation, queueName, responseBody);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueueProvisioningException("Jolokia " + operation + " interrupted for queue: " + queueName, e);
        } catch (QueueProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new QueueProvisioningException("Jolokia " + operation + " failed for queue: " + queueName, e);
        }
    }

}
