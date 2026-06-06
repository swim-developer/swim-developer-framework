package com.github.swim_developer.framework.integration.containers;

import com.github.swim_developer.framework.integration.tls.TlsTestCertificateGenerator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

public final class ArtemisTlsContainer implements AutoCloseable {

    private static final int AMQPS_PORT = 5671;
    private static final int AMQP_PORT = 5672;
    private static final int CONSOLE_PORT = 8161;
    private static final String CERTS_DIR = "/certs";
    private static final String BROKER_DIR = "/var/lib/artemis-instance";

    private final GenericContainer<?> container;
    private final TlsTestCertificateGenerator certs;

    public ArtemisTlsContainer(TlsTestCertificateGenerator certs) {
        this.certs = certs;

        String brokerXml = buildBrokerXml();
        String entrypoint = buildEntrypoint();

        this.container = new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.44.0"))
                .withExposedPorts(AMQPS_PORT, AMQP_PORT, CONSOLE_PORT)
                .withEnv("ARTEMIS_USER", "admin")
                .withEnv("ARTEMIS_PASSWORD", "admin")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(certs.getBrokerKeystorePath()),
                        CERTS_DIR + "/broker.p12")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(certs.getTruststorePath()),
                        CERTS_DIR + "/truststore.p12")
                .withCopyToContainer(
                        Transferable.of(brokerXml.getBytes(StandardCharsets.UTF_8)),
                        "/opt/broker-override/broker.xml")
                .withCopyToContainer(
                        Transferable.of(entrypoint.getBytes(StandardCharsets.UTF_8), 0755),
                        "/opt/tls-entrypoint.sh")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withEntrypoint("/opt/tls-entrypoint.sh"))
                .waitingFor(Wait.forLogMessage(".*AMQ221007.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public void start() {
        container.start();
    }

    public String getHost() {
        return container.getHost();
    }

    public int getAmqpsPort() {
        return container.getMappedPort(AMQPS_PORT);
    }

    public int getAmqpPort() {
        return container.getMappedPort(AMQP_PORT);
    }

    public String getAmqpsUrl() {
        return String.format("amqps://%s:%d", getHost(), getAmqpsPort());
    }

    public TlsTestCertificateGenerator getCerts() {
        return certs;
    }

    public Path getClientKeystorePath() {
        return certs.getClientKeystorePath();
    }

    public Path getRevokedClientKeystorePath() {
        return certs.getRevokedClientKeystorePath();
    }

    public Path getTruststorePath() {
        return certs.getTruststorePath();
    }

    public Path getCrlPath() {
        return certs.getCrlPath();
    }

    public String getKeystorePassword() {
        return certs.getKeystorePassword();
    }

    @Override
    public void close() {
        container.stop();
    }

    private String buildEntrypoint() {
        return "#!/bin/bash\n" +
                "set -e\n" +
                "# Create instance if not exists, then override broker.xml, then run\n" +
                "if [ ! -f " + BROKER_DIR + "/etc/broker.xml ]; then\n" +
                "  /opt/activemq-artemis/bin/artemis create " + BROKER_DIR + " \\\n" +
                "    --user admin --password admin --silent --require-login --no-autotune\n" +
                "fi\n" +
                "cp /opt/broker-override/broker.xml " + BROKER_DIR + "/etc/broker.xml\n" +
                "exec " + BROKER_DIR + "/bin/artemis run\n";
    }

    private String buildBrokerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<configuration xmlns=\"urn:activemq\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "               xsi:schemaLocation=\"urn:activemq /schema/artemis-configuration.xsd\">\n" +
                "    <core xmlns=\"urn:activemq:core\">\n" +
                "        <name>tls-test-broker</name>\n" +
                "        <persistence-enabled>false</persistence-enabled>\n" +
                "        <acceptors>\n" +
                "            <acceptor name=\"amqp\">tcp://0.0.0.0:" + AMQP_PORT +
                "?protocols=AMQP;amqpCredits=1000;amqpLowCredits=300</acceptor>\n" +
                "            <acceptor name=\"amqps\">tcp://0.0.0.0:" + AMQPS_PORT +
                "?protocols=AMQP" +
                ";sslEnabled=true" +
                ";keyStorePath=" + CERTS_DIR + "/broker.p12" +
                ";keyStorePassword=" + certs.getKeystorePassword() +
                ";keyStoreType=PKCS12" +
                ";trustStorePath=" + CERTS_DIR + "/truststore.p12" +
                ";trustStorePassword=" + certs.getKeystorePassword() +
                ";trustStoreType=PKCS12" +
                ";needClientAuth=true</acceptor>\n" +
                "        </acceptors>\n" +
                "        <security-settings>\n" +
                "            <security-setting match=\"#\">\n" +
                "                <permission type=\"createDurableQueue\" roles=\"amq\"/>\n" +
                "                <permission type=\"deleteDurableQueue\" roles=\"amq\"/>\n" +
                "                <permission type=\"createNonDurableQueue\" roles=\"amq\"/>\n" +
                "                <permission type=\"deleteNonDurableQueue\" roles=\"amq\"/>\n" +
                "                <permission type=\"createAddress\" roles=\"amq\"/>\n" +
                "                <permission type=\"deleteAddress\" roles=\"amq\"/>\n" +
                "                <permission type=\"consume\" roles=\"amq\"/>\n" +
                "                <permission type=\"browse\" roles=\"amq\"/>\n" +
                "                <permission type=\"send\" roles=\"amq\"/>\n" +
                "                <permission type=\"manage\" roles=\"amq\"/>\n" +
                "            </security-setting>\n" +
                "        </security-settings>\n" +
                "        <address-settings>\n" +
                "            <address-setting match=\"#\">\n" +
                "                <auto-create-queues>true</auto-create-queues>\n" +
                "                <auto-create-addresses>true</auto-create-addresses>\n" +
                "                <auto-delete-queues>true</auto-delete-queues>\n" +
                "                <auto-delete-addresses>true</auto-delete-addresses>\n" +
                "            </address-setting>\n" +
                "        </address-settings>\n" +
                "    </core>\n" +
                "</configuration>\n";
    }
}
