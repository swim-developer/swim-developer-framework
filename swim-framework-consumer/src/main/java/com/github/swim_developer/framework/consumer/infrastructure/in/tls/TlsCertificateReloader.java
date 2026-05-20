package com.github.swim_developer.framework.consumer.infrastructure.in.tls;

import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.port.out.SwimProviderConfigPort;
import com.github.swim_developer.framework.consumer.infrastructure.in.amqp.AmqpConnectionRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class TlsCertificateReloader {

    private final SwimProviderConfigPort providerConfig;
    private final AmqpConnectionRegistry amqpRegistry;
    private final boolean enabled;
    private final Map<String, Map<String, Long>> fileTimestamps = new ConcurrentHashMap<>();

    @Inject
    public TlsCertificateReloader(
            SwimProviderConfigPort providerConfig,
            AmqpConnectionRegistry amqpRegistry,
            @ConfigProperty(name = "swim.tls.reload-period") Optional<String> reloadPeriod) {
        this.providerConfig = providerConfig;
        this.amqpRegistry = amqpRegistry;
        this.enabled = reloadPeriod.isPresent() && !reloadPeriod.get().isBlank();
    }

    @Scheduled(every = "${swim.tls.reload-period:off}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkForCertificateChanges() {
        if (!enabled) {
            return;
        }

        providerConfig.getProviderMap().forEach(this::checkProvider);
    }

    void checkProvider(String providerId, ProviderConfiguration config) {
        List<String> files = collectCertFiles(config);
        if (files.isEmpty()) {
            return;
        }

        Map<String, Long> previous = fileTimestamps.get(providerId);
        Map<String, Long> current = snapshotTimestamps(files);

        if (previous == null) {
            fileTimestamps.put(providerId, current);
            return;
        }

        if (hasChanged(previous, current)) {
            log.info("TLS certificate change detected for provider '{}' — resetting connections", providerId);
            amqpRegistry.resetProvider(providerId);
            fileTimestamps.put(providerId, current);
            log.info("TLS reload complete for provider '{}'", providerId);
        }
    }

    private List<String> collectCertFiles(ProviderConfiguration config) {
        return Optional.ofNullable(config.amqpBroker())
                .map(amqp -> amqp.tls())
                .map(TlsConfig::allCertificateFiles)
                .orElse(List.of());
    }

    private Map<String, Long> snapshotTimestamps(List<String> files) {
        Map<String, Long> timestamps = new ConcurrentHashMap<>();
        for (String path : files) {
            File file = new File(path);
            if (file.exists()) {
                timestamps.put(path, file.lastModified());
            }
        }
        return timestamps;
    }

    private boolean hasChanged(Map<String, Long> previous, Map<String, Long> current) {
        if (previous.size() != current.size()) {
            return true;
        }
        for (var entry : current.entrySet()) {
            Long prevTimestamp = previous.get(entry.getKey());
            if (prevTimestamp == null || !prevTimestamp.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
