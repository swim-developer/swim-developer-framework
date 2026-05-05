package com.github.swim_developer.framework.consumer.infrastructure.out.client;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.model.TlsKeystoreType;
import com.github.swim_developer.framework.domain.exception.KeyStoreLoadException;
import com.github.swim_developer.framework.domain.exception.ProviderClientException;
import com.github.swim_developer.framework.infrastructure.out.resilience.ProviderCircuitBreaker;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractSubscriptionManagerClientRegistry<T> implements SwimSubscriptionManagerClientPort {

    private final Map<String, T> clients = new ConcurrentHashMap<>();
    private final ProviderCircuitBreaker circuitBreaker = new ProviderCircuitBreaker();

    public T getOrCreate(ProviderConfiguration provider) {
        return clients.computeIfAbsent(provider.providerId(),
                id -> buildClient(provider));
    }

    protected T buildClient(ProviderConfiguration provider) {
        var resilience = provider.subscriptionManager().effectiveResilience();
        log.info("Creating SM REST client for provider '{}' at {} [connectTimeout={}ms, readTimeout={}ms]",
                provider.providerId(), provider.subscriptionManager().url(),
                resilience.effectiveConnectTimeoutMs(), resilience.effectiveReadTimeoutMs());

        RestClientBuilder builder = RestClientBuilder.newBuilder()
                .baseUri(resolveBaseUri(provider))
                .connectTimeout(resilience.effectiveConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(resilience.effectiveReadTimeoutMs(), TimeUnit.MILLISECONDS);

        configureSsl(builder, provider);

        return builder.build(getClientClass());
    }

    protected abstract Class<T> getClientClass();

    public abstract String querySubscriptionStatus(String subscriptionId, ProviderConfiguration provider);

    protected URI resolveBaseUri(ProviderConfiguration provider) {
        return URI.create(provider.subscriptionManager().url());
    }

    public <R> R executeWithRetry(ProviderConfiguration provider, String operation, Supplier<R> action) {
        String providerId = provider.providerId();

        if (circuitBreaker.isOpen(providerId)) {
            throw new ProviderClientException("Circuit breaker OPEN for provider '" + providerId
                    + "' — SM '" + operation + "' rejected");
        }

        var resilience = provider.subscriptionManager().effectiveResilience();
        int maxAttempts = resilience.effectiveRetryMaxAttempts();
        long baseDelayMs = resilience.effectiveRetryDelayMs();
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                R result = action.get();
                handleSuccessAfterRetry(providerId, operation, attempt, maxAttempts);
                return result;
            } catch (WebApplicationException e) {
                if (isClientError(e)) {
                    throw e;
                }
                lastException = handleRetryableException(e, operation, attempt, maxAttempts, baseDelayMs, provider);
            } catch (RuntimeException e) {
                lastException = handleRetryableException(e, operation, attempt, maxAttempts, baseDelayMs, provider);
            }
        }

        circuitBreaker.recordFailure(providerId);
        if (lastException != null) {
            throw lastException;
        }
        throw new ProviderClientException("SM '" + operation + "' failed for provider '" + providerId + "' with no attempts");
    }

    private void handleSuccessAfterRetry(String providerId, String operation, int attempt, int maxAttempts) {
        circuitBreaker.recordSuccess(providerId);
        if (attempt > 1) {
            log.info("SM '{}' succeeded on attempt {}/{} for provider '{}'",
                    operation, attempt, maxAttempts, providerId);
        }
    }

    private RuntimeException handleRetryableException(RuntimeException exception, String operation,
                                                       int attempt, int maxAttempts, long baseDelayMs,
                                                       ProviderConfiguration provider) {
        long delayMs = calculateBackoffDelay(attempt, baseDelayMs);
        logRetryAttempt(operation, attempt, maxAttempts, delayMs, provider, exception);
        if (attempt < maxAttempts) {
            sleepBetweenRetries(delayMs);
        }
        return exception;
    }

    public void executeWithRetryVoid(ProviderConfiguration provider, String operation, Runnable action) {
        executeWithRetry(provider, operation, () -> {
            action.run();
            return null;
        });
    }

    static long calculateBackoffDelay(int attempt, long baseDelayMs) {
        return Math.min(baseDelayMs * (1L << (attempt - 1)), 30_000L);
    }

    private boolean isClientError(WebApplicationException e) {
        int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
        return status >= 400 && status < 500;
    }

    private void logRetryAttempt(String operation, int attempt, int maxAttempts,
                                  long delayMs, ProviderConfiguration provider, Exception e) {
        if (attempt < maxAttempts) {
            log.warn("SM '{}' failed (attempt {}/{}), retrying in {}ms for provider '{}': {}",
                    operation, attempt, maxAttempts, delayMs, provider.providerId(), e.getMessage());
        } else {
            log.error("SM '{}' exhausted all {}/{} attempts for provider '{}'",
                    operation, attempt, maxAttempts, provider.providerId());
        }
    }

    private void sleepBetweenRetries(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public ProviderCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    protected void configureSsl(RestClientBuilder builder, ProviderConfiguration provider) {
        var tls = provider.subscriptionManager().tls();
        if (tls == null) {
            return;
        }
        try {
            if (tls.enableRevocationCheck() && !tls.effectiveCrlPaths().isEmpty()) {
                configureSslWithCrl(builder, tls, provider.providerId());
            } else {
                configureSslBasic(builder, tls, provider.providerId());
            }
        } catch (KeyStoreLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderClientException(
                    "SSL configuration failed for provider '" + provider.providerId() + "': " + e.getMessage(), e);
        }
    }

    private void configureSslBasic(RestClientBuilder builder, TlsConfig tls, String providerId) throws Exception {
        if (hasValue(tls.trustStorePath())) {
            KeyStore trustStore = loadKeyStore(tls.trustStorePath(), tls.trustStorePassword(), tls.keystoreType());
            builder.trustStore(trustStore);
            log.info("SM truststore configured for provider '{}': {}", providerId, tls.trustStorePath());
        }
        if (hasValue(tls.keyStorePath())) {
            KeyStore keyStore = loadKeyStore(tls.keyStorePath(), tls.keyStorePassword(), tls.keystoreType());
            builder.keyStore(keyStore, tls.keyStorePassword());
            log.info("SM keystore configured for provider '{}': {}", providerId, tls.keyStorePath());
        }
    }

    private void configureSslWithCrl(RestClientBuilder builder, TlsConfig tls, String providerId) throws Exception {
        KeyStore trustStore = loadKeyStore(tls.trustStorePath(), tls.trustStorePassword(), tls.keystoreType());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509CRL> crls = new ArrayList<>();
        for (String path : tls.effectiveCrlPaths()) {
            try (var fis = new FileInputStream(path)) {
                crls.add((X509CRL) cf.generateCRL(fis));
            }
        }

        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustStore, new X509CertSelector());
        pkixParams.setRevocationEnabled(true);
        pkixParams.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(crls)));

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(new CertPathTrustManagerParameters(pkixParams));

        KeyManagerFactory kmf = null;
        if (hasValue(tls.keyStorePath())) {
            KeyStore keyStore = loadKeyStore(tls.keyStorePath(), tls.keyStorePassword(), tls.keystoreType());
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, tls.keyStorePassword() != null ? tls.keyStorePassword().toCharArray() : null);
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf != null ? kmf.getKeyManagers() : null, tmf.getTrustManagers(), null);
        builder.sslContext(sslContext);

        log.info("SM SSL with CRL revocation configured for provider '{}': {} CRL file(s)", providerId, crls.size());
    }

    protected KeyStore loadKeyStore(String path, String password, TlsKeystoreType type) throws KeyStoreLoadException {
        String storeType = resolveStoreType(path, type);
        try {
            KeyStore ks = KeyStore.getInstance(storeType);
            try (var is = new FileInputStream(new File(path))) {
                ks.load(is, password != null ? password.toCharArray() : null);
            }
            return ks;
        } catch (Exception e) {
            throw new KeyStoreLoadException(path, e);
        }
    }

    private String resolveStoreType(String path, TlsKeystoreType type) {
        if (type == null) {
            return path.endsWith(".p12") ? "PKCS12" : "JKS";
        }
        return switch (type) {
            case PKCS12 -> "PKCS12";
            case JKS -> "JKS";
            case PEM -> "JKS";
        };
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    public void clear() {
        clients.clear();
    }

    public int size() {
        return clients.size();
    }
}
