package com.github.swim_developer.framework.infrastructure.out.cluster;

import com.github.swim_developer.framework.application.port.out.LeaderElectionStrategy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
@DefaultBean
public class StandaloneLeaderElectionStrategy implements LeaderElectionStrategy {

    private final String hostname;

    @Inject
    public StandaloneLeaderElectionStrategy(
            @ConfigProperty(name = "HOSTNAME", defaultValue = "local-0") String hostname) {
        this.hostname = hostname;
    }

    @Override
    public void start() {
        log.info("Leader election [standalone] — always leader, Hostname: {}", hostname);
    }

    @Override
    public void stop() {
        log.debug("Standalone strategy stopped (Hostname: {})", hostname);
    }

    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public String getIdentity() {
        return hostname;
    }
}
