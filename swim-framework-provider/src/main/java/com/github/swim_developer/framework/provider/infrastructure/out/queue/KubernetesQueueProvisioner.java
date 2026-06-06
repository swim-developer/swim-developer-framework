package com.github.swim_developer.framework.provider.infrastructure.out.queue;

import com.github.swim_developer.framework.application.port.out.QueueProvisioningStrategy;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Kubernetes-based queue provisioning strategy using Secrets and AMQ Broker Operator.
 *
 * <h2>How It Works</h2>
 * <p>
 * This implementation provisions queues by writing configuration to Kubernetes Secrets:
 * </p>
 * <ol>
 *   <li>Queue configuration → {@code addressConfigurations.properties} Secret</li>
 *   <li>Security roles → {@code securityRoles.properties} Secret</li>
 *   <li>AMQ Broker Operator detects Secret changes and reconfigures broker automatically</li>
 * </ol>
 *
 * <h2>Secret Format</h2>
 * <pre>
 * # addressConfigurations.properties
 * addressConfigurations.DNOTAM.user1.abc123.routingTypes=ANYCAST
 * addressConfigurations.DNOTAM.user1.abc123.queueConfigs.DNOTAM.user1.abc123.address=DNOTAM.user1.abc123
 * addressConfigurations.DNOTAM.user1.abc123.queueConfigs.DNOTAM.user1.abc123.routingType=ANYCAST
 *
 * # securityRoles.properties
 * securityRoles.DNOTAM.user1.abc123.user1-amq.consume=true
 * securityRoles.DNOTAM.user1.abc123.admin.send=true
 * securityRoles.DNOTAM.user1.abc123.admin.manage=true
 * </pre>
 *
 * <h2>Secret Naming Convention</h2>
 * <p>
 * Secret names must end with {@code -bp} suffix to be recognized by the Broker Operator.
 * </p>
 *
 * <h2>Configuration</h2>
 * <pre>
 * # application.properties (prod - via env vars)
 * swim.kubernetes.namespace=${KUBERNETES_NAMESPACE:swim-demo}
 * swim.kubernetes.secret.address-configurations=${K8S_SECRET_ADDRESS:address-swim-configurations-bp}
 * swim.kubernetes.secret.security-roles=${K8S_SECRET_SECURITY:security-swim-roles-bp}
 * </pre>
 *
 * <h2>Activation</h2>
 * <p>
 * This bean is only active in {@code prod} profile via {@code @IfBuildProfile("prod")}.
 * </p>
 *
 * @since 1.0.0
 */
@ApplicationScoped
@IfBuildProfile("prod")
@Slf4j
public class KubernetesQueueProvisioner implements QueueProvisioningStrategy {

    private static final String ADDRESS_CONFIGURATIONS_KEY = "addressConfigurations.properties";
    private static final String SECURITY_ROLES_KEY = "securityRoles.properties";

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final String addressSecretName;
    private final String securitySecretName;

    @Inject
    public KubernetesQueueProvisioner(
            KubernetesClient kubernetesClient,
            @ConfigProperty(name = "swim.kubernetes.namespace") String namespace,
            @ConfigProperty(name = "swim.kubernetes.secret.address-configurations") String addressSecretName,
            @ConfigProperty(name = "swim.kubernetes.secret.security-roles") String securitySecretName) {
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
        this.addressSecretName = addressSecretName;
        this.securitySecretName = securitySecretName;
    }

    @Override
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public void createQueue(String queueName) {
        log.info("Provisioning queue via Kubernetes Secret: {}", queueName);
        String newConfig = buildAddressConfiguration(queueName);
        appendToSecret(addressSecretName, ADDRESS_CONFIGURATIONS_KEY, newConfig);
    }

    @Override
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public void addSecurityRole(String queueName, String username, String roleSuffix) {
        String roleName = username + roleSuffix;
        log.info("Provisioning security role via Kubernetes Secret - Queue: {}, Role: {}", queueName, roleName);
        String newConfig = buildSecurityRoleConfiguration(queueName, roleName);
        appendToSecret(securitySecretName, SECURITY_ROLES_KEY, newConfig);
    }

    @Override
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public void removeQueue(String queueName) {
        log.info("Removing queue via Kubernetes Secret: {}", queueName);
        removeFromSecret(addressSecretName, ADDRESS_CONFIGURATIONS_KEY, "addressConfigurations." + queueName);
    }

    @Override
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public void removeSecurityRole(String queueName) {
        log.info("Removing security role via Kubernetes Secret: {}", queueName);
        removeFromSecret(securitySecretName, SECURITY_ROLES_KEY, "securityRoles." + queueName);
    }

    private String buildAddressConfiguration(String queueName) {
        return String.format("""
                addressConfigurations.%s.routingTypes=ANYCAST
                addressConfigurations.%s.queueConfigs.%s.address=%s
                addressConfigurations.%s.queueConfigs.%s.routingType=ANYCAST
                """, queueName, queueName, queueName, queueName, queueName, queueName);
    }

    private String buildSecurityRoleConfiguration(String queueName, String roleName) {
        return String.format("""
                securityRoles.%s.%s.consume=true
                securityRoles.%s.admin.send=true
                securityRoles.%s.admin.manage=true
                """, queueName, roleName, queueName, queueName);
    }

    private void appendToSecret(String secretName, String key, String contentToAppend) {
        Secret secret = getOrCreateSecret(secretName);
        Map<String, String> data = secret.getData() != null ? secret.getData() : new HashMap<>();

        String existingContent = data.containsKey(key) ? decodeBase64(data.get(key)) : "";
        if (!existingContent.isEmpty() && !existingContent.endsWith("\n")) {
            existingContent += "\n";
        }
        data.put(key, encodeBase64(existingContent + contentToAppend));
        secret.setData(data);

        kubernetesClient.secrets().inNamespace(namespace).resource(secret).update();
    }

    private void removeFromSecret(String secretName, String key, String prefixToRemove) {
        Secret secret = kubernetesClient.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret == null || secret.getData() == null || !secret.getData().containsKey(key)) {
            return;
        }

        String existingContent = decodeBase64(secret.getData().get(key));
        StringBuilder newContent = new StringBuilder();
        for (String line : existingContent.split("\n")) {
            if (!line.startsWith(prefixToRemove)) {
                newContent.append(line).append("\n");
            }
        }

        secret.getData().put(key, encodeBase64(newContent.toString()));
        kubernetesClient.secrets().inNamespace(namespace).resource(secret).update();
    }

    private Secret getOrCreateSecret(String secretName) {
        Secret secret = kubernetesClient.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret != null) {
            return secret;
        }
        if (!secretName.endsWith("-bp")) {
            throw new IllegalArgumentException("Secret name must end with '-bp': " + secretName);
        }
        secret = new SecretBuilder()
                .withNewMetadata().withName(secretName).withNamespace(namespace).endMetadata()
                .withType("Opaque").withData(new HashMap<>())
                .build();
        return kubernetesClient.secrets().inNamespace(namespace).resource(secret).create();
    }

    private String encodeBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeBase64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
