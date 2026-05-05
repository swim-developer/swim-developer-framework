package com.github.swim_developer.framework.consumer.infrastructure.in.amqp;

import com.github.swim_developer.framework.application.model.AmqpBrokerConfig;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.SaslMechanism;
import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.model.TlsKeystoreType;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class AmqpConnectionRegistry {

    private final int connectTimeout;
    private final int idleTimeout;
    private final String containerIdPrefix;
    private final Map<String, AmqpClient> connections = new ConcurrentHashMap<>();

    @jakarta.inject.Inject
    public AmqpConnectionRegistry(
            @ConfigProperty(name = "swim.amqp.connect-timeout", defaultValue = "10000") int connectTimeout,
            @ConfigProperty(name = "swim.amqp.idle-timeout", defaultValue = "300000") int idleTimeout,
            @ConfigProperty(name = "swim.amqp.container-id-prefix", defaultValue = "swim-consumer") String containerIdPrefix) {
        this.connectTimeout = connectTimeout;
        this.idleTimeout = idleTimeout;
        this.containerIdPrefix = containerIdPrefix;
    }

    public AmqpClient getOrCreate(ProviderConfiguration provider, Vertx vertx) {
        return connections.computeIfAbsent(provider.providerId(),
                id -> createAmqpClient(provider, vertx));
    }

    private AmqpClient createAmqpClient(ProviderConfiguration provider, Vertx vertx) {
        var amqp = provider.amqpBroker();
        AmqpClientOptions options = new AmqpClientOptions()
                .setHost(amqp.host())
                .setPort(amqp.port())
                .setSsl(amqp.sslEnabled())
                .setConnectTimeout(connectTimeout)
                .setIdleTimeout(idleTimeout)
                .setContainerId(containerIdPrefix + "-" + provider.providerId());

        if (amqp.sslEnabled()) {
            options.setSniServerName(amqp.host())
                    .setHostnameVerificationAlgorithm("HTTPS");

            configureTls(options, amqp);
        }

        configureSasl(options, amqp);

        log.info("Creating AMQP client for provider '{}' - Host: {}:{}, SSL: {}, SASL: {}, ContainerId: {}",
                provider.providerId(), amqp.host(), amqp.port(), amqp.sslEnabled(),
                amqp.saslMechanism(), options.getContainerId());

        return AmqpClient.create(vertx, options);
    }

    private void configureTls(AmqpClientOptions options, AmqpBrokerConfig amqp) {
        TlsConfig tls = amqp.tls();
        if (tls == null) {
            return;
        }

        TlsKeystoreType keystoreType = tls.keystoreType() != null ? tls.keystoreType() : TlsKeystoreType.JKS;

        configureTrustStore(options, tls, keystoreType);
        configureKeyStore(options, tls, keystoreType);
        configureRevocation(options, tls);
    }

    private void configureTrustStore(AmqpClientOptions options, TlsConfig tls, TlsKeystoreType type) {
        switch (type) {
            case TlsKeystoreType k when k == TlsKeystoreType.JKS && hasValue(tls.trustStorePath()) -> {
                options.setTrustStoreOptions(new JksOptions()
                        .setPath(tls.trustStorePath())
                        .setPassword(tls.trustStorePassword()));
                log.info("TLS truststore (JKS): {}", tls.trustStorePath());
            }
            case TlsKeystoreType k when k == TlsKeystoreType.PKCS12 && hasValue(tls.trustStorePath()) -> {
                options.setPfxTrustOptions(new PfxOptions()
                        .setPath(tls.trustStorePath())
                        .setPassword(tls.trustStorePassword()));
                log.info("TLS truststore (PKCS12): {}", tls.trustStorePath());
            }
            case TlsKeystoreType k when k == TlsKeystoreType.PEM && hasValue(tls.certPath()) -> {
                options.setPemTrustOptions(new PemTrustOptions()
                        .addCertPath(tls.certPath()));
                log.info("TLS truststore (PEM): {}", tls.certPath());
            }
            default -> { /* no truststore configured — path or type not applicable */ }
        }
    }

    private void configureKeyStore(AmqpClientOptions options, TlsConfig tls, TlsKeystoreType type) {
        switch (type) {
            case TlsKeystoreType k when k == TlsKeystoreType.JKS && hasValue(tls.keyStorePath()) -> {
                options.setKeyCertOptions(new JksOptions()
                        .setPath(tls.keyStorePath())
                        .setPassword(tls.keyStorePassword()));
                log.info("TLS keystore (JKS): {}", tls.keyStorePath());
            }
            case TlsKeystoreType k when k == TlsKeystoreType.PKCS12 && hasValue(tls.keyStorePath()) -> {
                options.setPfxKeyCertOptions(new PfxOptions()
                        .setPath(tls.keyStorePath())
                        .setPassword(tls.keyStorePassword()));
                log.info("TLS keystore (PKCS12): {}", tls.keyStorePath());
            }
            case TlsKeystoreType k when k == TlsKeystoreType.PEM && hasValue(tls.certPath())
                    && hasValue(tls.keyPath()) -> {
                options.setPemKeyCertOptions(new PemKeyCertOptions()
                        .addCertPath(tls.certPath())
                        .addKeyPath(tls.keyPath()));
                log.info("TLS key/cert (PEM): cert={}, key={}", tls.certPath(), tls.keyPath());
            }
            default -> { /* no keystore configured — path or type not applicable */ }
        }
    }

    private void configureRevocation(AmqpClientOptions options, TlsConfig tls) {
        if (tls.enableRevocationCheck()) {
            var paths = tls.effectiveCrlPaths();
            for (String path : paths) {
                options.addCrlPath(path);
            }
            if (!paths.isEmpty()) {
                log.info("CRL revocation enabled with {} file(s): {}", paths.size(), paths);
            }
        }

        if (tls.ocspEnabled()) {
            log.warn("OCSP revocation requested but requires a custom TrustManager — not yet implemented. " +
                    "Use CRL as an alternative until OCSP support is added.");
        }
    }

    private void configureSasl(AmqpClientOptions options, AmqpBrokerConfig amqp) {
        SaslMechanism mechanism = amqp.saslMechanism() != null ? amqp.saslMechanism() : SaslMechanism.PLAIN;

        switch (mechanism) {
            case PLAIN -> {
                if (hasValue(amqp.username())) {
                    options.setUsername(amqp.username());
                    log.info("SASL PLAIN configured — username: {}", amqp.username());
                }
                if (hasValue(amqp.password())) {
                    options.setPassword(amqp.password());
                }
            }
            case ANONYMOUS -> log.info("SASL ANONYMOUS configured — no credentials sent");
            case EXTERNAL -> log.info("SASL EXTERNAL configured — identity from client certificate");
        }
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    public void resetProvider(String providerId) {
        AmqpClient client = connections.remove(providerId);
        if (client != null) {
            try {
                client.close();
                log.info("AMQP client closed for provider '{}'", providerId);
            } catch (Exception e) {
                log.warn("Error closing AMQP client for provider '{}': {}", providerId, e.getMessage());
            }
        }
    }

    public boolean hasConnection(String providerId) {
        return connections.containsKey(providerId);
    }

    public int getConnectionCount() {
        return connections.size();
    }

    @PreDestroy
    void cleanup() {
        connections.forEach((providerId, client) -> {
            try {
                client.close();
                log.info("AMQP client closed for provider '{}'", providerId);
            } catch (Exception e) {
                log.warn("Error closing AMQP client for provider '{}': {}", providerId, e.getMessage());
            }
        });
        connections.clear();
    }
}
