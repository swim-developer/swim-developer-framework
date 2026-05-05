package com.github.swim_developer.framework.leader.kubernetes;

import com.github.swim_developer.framework.application.port.out.LeaderElectionStrategy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@ApplicationScoped
public class KubernetesLeaderElectionStrategy implements LeaderElectionStrategy {

    private final KubernetesClient client;
    private final String hostname;
    private final String leaseName;
    private final Optional<String> leaseNamespace;
    private final int leaseDurationSeconds;
    private final int renewDeadlineSeconds;
    private final int retryPeriodSeconds;

    private volatile boolean leader;
    private ExecutorService leaseExecutor;

    @Inject
    public KubernetesLeaderElectionStrategy(
            KubernetesClient client,
            @ConfigProperty(name = "HOSTNAME", defaultValue = "local-0") String hostname,
            @ConfigProperty(name = "swim.leader-election.lease-name", defaultValue = "swim-leader") String leaseName,
            @ConfigProperty(name = "swim.leader-election.lease-namespace") Optional<String> leaseNamespace,
            @ConfigProperty(name = "swim.leader-election.lease-duration-seconds", defaultValue = "15") int leaseDurationSeconds,
            @ConfigProperty(name = "swim.leader-election.renew-deadline-seconds", defaultValue = "10") int renewDeadlineSeconds,
            @ConfigProperty(name = "swim.leader-election.retry-period-seconds", defaultValue = "2") int retryPeriodSeconds) {
        this.client = client;
        this.hostname = hostname;
        this.leaseName = leaseName;
        this.leaseNamespace = leaseNamespace;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.renewDeadlineSeconds = renewDeadlineSeconds;
        this.retryPeriodSeconds = retryPeriodSeconds;
    }

    @Override
    public void start() {
        log.info("Leader election [kubernetes] — Pod: {}, Lease: {}", hostname, leaseName);
        startLeaseElection();
    }

    private void startLeaseElection() {
        String ns = resolveNamespace();
        LeaseLock lock = new LeaseLock(ns, leaseName, hostname);

        var config = new LeaderElectionConfigBuilder()
                .withName(leaseName)
                .withLeaseDuration(Duration.ofSeconds(leaseDurationSeconds))
                .withRenewDeadline(Duration.ofSeconds(renewDeadlineSeconds))
                .withRetryPeriod(Duration.ofSeconds(retryPeriodSeconds))
                .withLock(lock)
                .withLeaderCallbacks(new LeaderCallbacks(
                        this::onStartLeading,
                        this::onStopLeading,
                        this::onNewLeader))
                .withReleaseOnCancel()
                .build();

        leaseExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "k8s-lease-election-" + hostname);
            t.setDaemon(true);
            return t;
        });

        leaseExecutor.submit(() -> {
            try {
                client.leaderElector().withConfig(config).build().run();
            } catch (Exception e) {
                log.error("Kubernetes lease election terminated: {}", e.getMessage());
                leader = false;
            }
        });
    }

    private void onStartLeading() {
        leader = true;
        log.info("Leadership ACQUIRED (Pod: {})", hostname);
    }

    private void onStopLeading() {
        leader = false;
        log.warn("Leadership LOST (Pod: {})", hostname);
    }

    private void onNewLeader(String newLeader) {
        log.info("Current leader: {}", newLeader);
    }

    private String resolveNamespace() {
        return leaseNamespace
                .filter(ns -> !ns.isBlank())
                .orElseGet(client::getNamespace);
    }

    @Override
    public void stop() {
        if (leaseExecutor != null) {
            leaseExecutor.shutdownNow();
            log.info("Kubernetes lease election stopped (Pod: {})", hostname);
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
