package com.github.swim_developer.framework.leader.infinispan;

import com.github.swim_developer.framework.application.port.out.LeaderElectionStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCache;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class InfinispanLeaderElectionStrategy implements LeaderElectionStrategy {

    private static final String LEADER_KEY = "swim-leader";

    private final RemoteCacheManager cacheManager;
    private final String hostname;
    private final int lockTtlSeconds;
    private final int refreshIntervalSeconds;

    private volatile boolean leader;
    private ScheduledExecutorService scheduler;
    private BasicCache<String, String> cache;

    @Inject
    public InfinispanLeaderElectionStrategy(
            RemoteCacheManager cacheManager,
            @ConfigProperty(name = "HOSTNAME", defaultValue = "local-0") String hostname,
            @ConfigProperty(name = "swim.leader-election.lock-ttl-seconds", defaultValue = "15") int lockTtlSeconds,
            @ConfigProperty(name = "swim.leader-election.refresh-interval-seconds", defaultValue = "5") int refreshIntervalSeconds) {
        this.cacheManager = cacheManager;
        this.hostname = hostname;
        this.lockTtlSeconds = lockTtlSeconds;
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    @Override
    public void start() {
        log.info("Leader election [infinispan] — Instance: {}", hostname);
        cache = cacheManager.getCache();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "infinispan-leader-" + hostname);
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::tryAcquireLeadership, 0, refreshIntervalSeconds, TimeUnit.SECONDS);
    }

    private void tryAcquireLeadership() {
        try {
            String currentLeader = cache.putIfAbsent(LEADER_KEY, hostname, lockTtlSeconds, TimeUnit.SECONDS);

            if (currentLeader == null || currentLeader.equals(hostname)) {
                if (!leader) {
                    leader = true;
                    log.info("Leadership ACQUIRED (Instance: {})", hostname);
                }
                cache.put(LEADER_KEY, hostname, lockTtlSeconds, TimeUnit.SECONDS);
            } else {
                if (leader) {
                    leader = false;
                    log.warn("Leadership LOST (Instance: {}, Current leader: {})", hostname, currentLeader);
                }
            }
        } catch (Exception e) {
            log.error("Infinispan leader election failed: {}", e.getMessage());
            leader = false;
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("Infinispan leader election stopped (Instance: {})", hostname);
        }
        if (leader && cache != null) {
            try {
                cache.remove(LEADER_KEY, hostname);
            } catch (Exception e) {
                log.warn("Failed to release leadership on shutdown: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isLeader() {
        return leader;
    }

    @Override
    public String getIdentity() {
        return hostname;
    }
}
