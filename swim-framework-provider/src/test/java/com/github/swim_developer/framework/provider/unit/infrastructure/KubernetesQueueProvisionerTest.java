package com.github.swim_developer.framework.provider.unit.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.infrastructure.out.queue.KubernetesQueueProvisioner;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("infrastructure")
@SuppressWarnings("unchecked")
@ExtendWith(TestNameLoggerExtension.class)
class KubernetesQueueProvisionerTest {

    private KubernetesQueueProvisioner provisioner;
    private KubernetesClient kubernetesClient;
    private MixedOperation secretsOp;
    private NonNamespaceOperation namespacedOp;
    private Secret capturedSecret;

    @BeforeEach
    void setUp() {
        kubernetesClient = mock(KubernetesClient.class);
        secretsOp = mock(MixedOperation.class);
        namespacedOp = mock(NonNamespaceOperation.class);

        when(kubernetesClient.secrets()).thenReturn(secretsOp);
        when(secretsOp.inNamespace("swim-ns")).thenReturn(namespacedOp);

        provisioner = new KubernetesQueueProvisioner(
            kubernetesClient,
            "swim-ns",
            "amq-config-bp",
            "amq-security-bp"
        );
    }

    @Test
    void createQueueAppendsToExistingSecret() {
        Secret existing = secretWithData("addressConfigurations.properties", "existing=true\n");
        stubGetSecret("amq-config-bp", existing);
        stubUpdateSecret();

        provisioner.createQueue("DNOTAM.v1.sub-1");

        assertThat(decodeCaptured("addressConfigurations.properties"))
                .startsWith("existing=true\n")
                .contains("addressConfigurations.DNOTAM.v1.sub-1.routingTypes=ANYCAST")
                .contains("addressConfigurations.DNOTAM.v1.sub-1.queueConfigs.DNOTAM.v1.sub-1.routingType=ANYCAST");
    }

    @Test
    void addSecurityRoleBuildsCorrectEntries() {
        Secret existing = secretWithData("securityRoles.properties", "");
        stubGetSecret("amq-security-bp", existing);
        stubUpdateSecret();

        provisioner.addSecurityRole("DNOTAM.v1.sub-1", "swim-user", "-amq");

        assertThat(decodeCaptured("securityRoles.properties"))
                .contains("securityRoles.DNOTAM.v1.sub-1.swim-user-amq.consume=true")
                .contains("securityRoles.DNOTAM.v1.sub-1.admin.send=true")
                .contains("securityRoles.DNOTAM.v1.sub-1.admin.manage=true");
    }

    @Test
    void removeQueueStripsMatchingLines() {
        String content = """
                addressConfigurations.q-1.routingTypes=ANYCAST
                addressConfigurations.q-2.routingTypes=ANYCAST
                """;
        Secret existing = secretWithData("addressConfigurations.properties", content);
        stubGetSecret("amq-config-bp", existing);
        stubUpdateSecret();

        provisioner.removeQueue("q-1");

        String result = decodeCaptured("addressConfigurations.properties");
        assertThat(result).doesNotContain("q-1").contains("q-2");
    }

    @Test
    void removeQueueDoesNothingWhenSecretMissing() {
        stubGetSecret("amq-config-bp", null);

        provisioner.removeQueue("q-1");

        verify(namespacedOp, never()).resource(any(Secret.class));
    }

    @Test
    void createQueueCreatesSecretWhenMissingWithBpSuffix() {
        stubGetSecret("amq-config-bp", null);
        NamespaceableResource createdResource = mock(NamespaceableResource.class);
        when(namespacedOp.resource(any(Secret.class))).thenReturn(createdResource);
        Secret freshSecret = new SecretBuilder()
                .withNewMetadata().withName("amq-config-bp").withNamespace("swim-ns").endMetadata()
                .withType("Opaque").withData(new HashMap<>()).build();
        when(createdResource.create()).thenReturn(freshSecret);
        when(createdResource.update()).thenReturn(freshSecret);

        provisioner.createQueue("q-new");

        verify(createdResource).create();
        verify(createdResource).update();
    }

    @Test
    void getOrCreateSecretRejectsNameWithoutBpSuffix() {
        KubernetesQueueProvisioner badProvisioner = new KubernetesQueueProvisioner(
            kubernetesClient,
            "swim-ns",
            "bad-name",
            "amq-security-bp"
        );
        stubGetSecret("bad-name", null);

        assertThatThrownBy(() -> badProvisioner.createQueue("q-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-bp");
    }

    private Secret secretWithData(String key, String value) {
        Map<String, String> data = new HashMap<>();
        data.put(key, encode(value));
        return new SecretBuilder()
                .withNewMetadata().withName("test-secret").endMetadata()
                .withData(data).build();
    }

    private void stubGetSecret(String name, Secret secret) {
        io.fabric8.kubernetes.client.dsl.Resource resource = mock(io.fabric8.kubernetes.client.dsl.Resource.class);
        when(resource.get()).thenReturn(secret);
        when(namespacedOp.withName(name)).thenReturn(resource);
    }

    private void stubUpdateSecret() {
        NamespaceableResource resource = mock(NamespaceableResource.class);
        when(namespacedOp.resource(any(Secret.class))).thenAnswer(inv -> {
            capturedSecret = inv.getArgument(0);
            return resource;
        });
        when(resource.update()).thenReturn(null);
    }

    private String decodeCaptured(String key) {
        assertThat(capturedSecret).isNotNull();
        assertThat(capturedSecret.getData()).containsKey(key);
        return new String(Base64.getDecoder().decode(capturedSecret.getData().get(key)), StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
