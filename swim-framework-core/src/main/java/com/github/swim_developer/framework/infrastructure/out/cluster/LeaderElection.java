package com.github.swim_developer.framework.infrastructure.out.cluster;

import com.github.swim_developer.framework.application.port.out.LeaderElectionStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LeaderElection {

    private final LeaderElectionStrategy strategy;

    @Inject
    public LeaderElection(LeaderElectionStrategy strategy) {
        this.strategy = strategy;
    }

    @PostConstruct
    void init() {
        strategy.start();
    }

    @PreDestroy
    void cleanup() {
        strategy.stop();
    }

    public boolean isLeader() {
        return strategy.isLeader();
    }

    public String getHostname() {
        return strategy.getIdentity();
    }
}
