package com.github.swim_developer.framework.consumer.integration.tls;

import com.github.swim_developer.framework.application.model.AmqpBrokerConfig;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.SaslMechanism;
import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.model.TlsKeystoreType;
import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AmqpConnectionRegistry;
import com.github.swim_developer.framework.integration.containers.ArtemisTlsContainer;
import com.github.swim_developer.framework.integration.tls.TlsTestCertificateGenerator;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpMessage;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SWIM-TIYP-0037: AMQP Transport Security Authentication
 * SWIM-TIYP-0033: TLS server authentication
 * SWIM-TIYP-0065: Cryptographic Algorithms (TLS 1.2+)
 * Reference: EUROCONTROL SPEC-170, Section 3.2
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TlsSecurityIT {

    private static TlsTestCertificateGenerator certs;
    private static ArtemisTlsContainer artemis;
    private static Vertx vertx;

    @BeforeAll
    static void setup() throws Exception {
        certs = TlsTestCertificateGenerator.generateAll();
        artemis = new ArtemisTlsContainer(certs);
        artemis.start();
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void teardown() {
        if (artemis != null) artemis.close();
        if (vertx != null) vertx.close();
    }

    @Test
    @Order(1)
    void mtlsAmqpConnectionSucceeds() throws Exception {
        var config = buildAmqpProviderConfig(
                certs.getClientKeystorePath().toString(),
                certs.getTruststorePath().toString(),
                null);

        AmqpConnectionRegistry registry = new AmqpConnectionRegistry(10000, 30000, "tls-test");

        AmqpClient client = registry.getOrCreate(config, vertx);
        assertThat(client).isNotNull();

        CompletableFuture<String> received = new CompletableFuture<>();

        client.connect().onComplete(conn -> {
            if (conn.failed()) {
                received.completeExceptionally(conn.cause());
                return;
            }
            var connection = conn.result();
            connection.createReceiver("tls-test-queue").onComplete(recv -> {
                if (recv.failed()) {
                    received.completeExceptionally(recv.cause());
                    return;
                }
                recv.result().handler(msg -> received.complete(msg.bodyAsString()));

                connection.createSender("tls-test-queue").onComplete(send -> {
                    if (send.failed()) {
                        received.completeExceptionally(send.cause());
                        return;
                    }
                    send.result().send(AmqpMessage.create().withBody("mTLS-OK").build());
                });
            });
        });

        String result = received.get(15, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("mTLS-OK");
        registry.resetProvider(config.providerId());
    }

    @Test
    @Order(2)
    void mtlsAmqpRejectsWithoutClientCert() {
        TlsConfig tlsNoClient = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PKCS12)
                .trustStorePath(certs.getTruststorePath().toString())
                .trustStorePassword(certs.getKeystorePassword())
                .build();

        AmqpBrokerConfig amqp = AmqpBrokerConfig.builder()
                .host(artemis.getHost())
                .port(artemis.getAmqpsPort())
                .sslEnabled(true)
                .username("admin")
                .password("admin")
                .saslMechanism(SaslMechanism.PLAIN)
                .tls(tlsNoClient)
                .build();

        ProviderConfiguration config = ProviderConfiguration.builder()
                .providerId("no-client-cert")
                .amqpBroker(amqp)
                .build();

        AmqpConnectionRegistry registry = new AmqpConnectionRegistry(5000, 10000, "tls-reject");

        AmqpClient client = registry.getOrCreate(config, vertx);
        CompletableFuture<Void> connectionAttempt = new CompletableFuture<>();

        client.connect().onComplete(conn -> {
            if (conn.failed()) {
                connectionAttempt.completeExceptionally(conn.cause());
            } else {
                conn.result().createSender("reject-queue").onComplete(send -> {
                    if (send.failed()) {
                        connectionAttempt.completeExceptionally(send.cause());
                    } else {
                        connectionAttempt.complete(null);
                    }
                });
            }
        });

        assertThatThrownBy(() -> connectionAttempt.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(Exception.class);
        registry.resetProvider(config.providerId());
    }

    @Test
    @Order(3)
    void mtlsRestClientConnectsToHttpsServer() throws Exception {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();

        HttpServerOptions serverOpts = new HttpServerOptions()
                .setSsl(true)
                .setClientAuth(ClientAuth.REQUIRED)
                .setKeyCertOptions(new PfxOptions()
                        .setPath(certs.getBrokerKeystorePath().toString())
                        .setPassword(certs.getKeystorePassword()))
                .setTrustOptions(new PfxOptions()
                        .setPath(certs.getTruststorePath().toString())
                        .setPassword(certs.getKeystorePassword()));

        HttpServer server = vertx.createHttpServer(serverOpts);
        server.requestHandler(req -> req.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"OK\"}"));

        server.listen(0).onComplete(r -> {
            if (r.succeeded()) portFuture.complete(r.result().actualPort());
            else portFuture.completeExceptionally(r.cause());
        });

        int port = portFuture.get(10, TimeUnit.SECONDS);

        HttpClientOptions clientOpts = new HttpClientOptions()
                .setSsl(true)
                .setTrustOptions(new PfxOptions()
                        .setPath(certs.getTruststorePath().toString())
                        .setPassword(certs.getKeystorePassword()))
                .setKeyCertOptions(new PfxOptions()
                        .setPath(certs.getClientKeystorePath().toString())
                        .setPassword(certs.getKeystorePassword()));

        var httpClient = vertx.createHttpClient(clientOpts);
        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        httpClient.request(HttpMethod.GET, port, "localhost", "/")
                .compose(req -> req.send())
                .compose(resp -> resp.body())
                .onSuccess(buf -> responseFuture.complete(buf.toString()))
                .onFailure(responseFuture::completeExceptionally);

        String body = responseFuture.get(10, TimeUnit.SECONDS);
        assertThat(body).contains("OK");

        server.close();
    }

    @Test
    @Order(4)
    void crlRevocationRejectsRevokedServerCert() throws Exception {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();

        HttpServerOptions serverOpts = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(new PfxOptions()
                        .setPath(certs.getRevokedClientKeystorePath().toString())
                        .setPassword(certs.getKeystorePassword()));

        HttpServer server = vertx.createHttpServer(serverOpts);
        server.requestHandler(req -> req.response().end("should-not-reach"));

        server.listen(0).onComplete(r -> {
            if (r.succeeded()) portFuture.complete(r.result().actualPort());
            else portFuture.completeExceptionally(r.cause());
        });

        int port = portFuture.get(10, TimeUnit.SECONDS);

        HttpClientOptions clientOpts = new HttpClientOptions()
                .setSsl(true)
                .setVerifyHost(false)
                .setTrustOptions(new PfxOptions()
                        .setPath(certs.getTruststorePath().toString())
                        .setPassword(certs.getKeystorePassword()))
                .addCrlPath(certs.getCrlPath().toString());

        var httpClient = vertx.createHttpClient(clientOpts);
        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        httpClient.request(HttpMethod.GET, port, "localhost", "/")
                .compose(req -> req.send())
                .compose(resp -> resp.body())
                .onSuccess(buf -> responseFuture.complete(buf.toString()))
                .onFailure(responseFuture::completeExceptionally);

        assertThatThrownBy(() -> responseFuture.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(Exception.class);

        server.close();
    }

    @Test
    @Order(5)
    void multipleCrlPathsSupported() {
        TlsConfig tls = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PKCS12)
                .trustStorePath(certs.getTruststorePath().toString())
                .trustStorePassword(certs.getKeystorePassword())
                .keyStorePath(certs.getClientKeystorePath().toString())
                .keyStorePassword(certs.getKeystorePassword())
                .enableRevocationCheck(true)
                .crlPaths(List.of(
                        certs.getCrlPath().toString(),
                        certs.getCrlPath().toString()))
                .build();

        assertThat(tls.effectiveCrlPaths()).hasSize(2);
        assertThat(tls.allCertificateFiles()).contains(certs.getCrlPath().toString());
    }

    @Test
    @Order(6)
    void certificateReloadDetectsFileChange() throws Exception {
        var config = buildAmqpProviderConfig(
                certs.getClientKeystorePath().toString(),
                certs.getTruststorePath().toString(),
                null);

        AmqpConnectionRegistry registry = new AmqpConnectionRegistry(10000, 30000, "tls-reload");

        AmqpClient firstClient = registry.getOrCreate(config, vertx);
        assertThat(registry.hasConnection("reload-test")).isTrue();

        var newCerts = TlsTestCertificateGenerator.generateAll();
        Files.copy(newCerts.getClientKeystorePath(), certs.getClientKeystorePath(),
                StandardCopyOption.REPLACE_EXISTING);

        registry.resetProvider("reload-test");
        assertThat(registry.hasConnection("reload-test")).isFalse();

        AmqpClient secondClient = registry.getOrCreate(config, vertx);
        assertThat(secondClient).isNotNull().isNotSameAs(firstClient);
        assertThat(registry.hasConnection("reload-test")).isTrue();

        registry.resetProvider("reload-test");
    }

    private ProviderConfiguration buildAmqpProviderConfig(String keystorePath, String truststorePath, String crlPath) {
        TlsConfig.TlsConfigBuilder tlsBuilder = TlsConfig.builder()
                .keystoreType(TlsKeystoreType.PKCS12)
                .trustStorePath(truststorePath)
                .trustStorePassword(certs.getKeystorePassword())
                .keyStorePath(keystorePath)
                .keyStorePassword(certs.getKeystorePassword());

        if (crlPath != null) {
            tlsBuilder.enableRevocationCheck(true).crlPath(crlPath);
        }

        AmqpBrokerConfig amqp = AmqpBrokerConfig.builder()
                .host(artemis.getHost())
                .port(artemis.getAmqpsPort())
                .sslEnabled(true)
                .username("admin")
                .password("admin")
                .saslMechanism(SaslMechanism.PLAIN)
                .tls(tlsBuilder.build())
                .build();

        return ProviderConfiguration.builder()
                .providerId("reload-test")
                .amqpBroker(amqp)
                .build();
    }

}
