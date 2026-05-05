package com.github.swim_developer.framework.application.port.out;

/**
 * SPI for cluster leader election. Ensures only one instance executes scheduled tasks
 * or singleton operations in a multi-instance deployment.
 *
 * <p><b>Available implementations:</b>
 * <ul>
 *   <li>{@code KubernetesLeaderElectionStrategy} - Kubernetes Lease-based election</li>
 *   <li>{@code InfinispanLeaderElectionStrategy} - Infinispan distributed lock</li>
 *   <li>{@code StandaloneLeaderElectionStrategy} - Single instance (always leader)</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @ApplicationScoped
 * public class MyScheduler {
 *     @Inject LeaderElectionStrategy leaderElection;
 *
 *     @Scheduled(every = "60s")
 *     void runIfLeader() {
 *         if (leaderElection.isLeader()) {
 *             // Execute singleton task
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see com.github.swim_developer.framework.infrastructure.out.cluster.StandaloneLeaderElectionStrategy
 */
public interface LeaderElectionStrategy {

    /**
     * Starts the leader election process. Must be called before {@link #isLeader()}.
     * Implementations should handle reconnection on failure.
     */
    void start();

    /**
     * Stops the leader election and releases leadership if held.
     */
    void stop();

    /**
     * Checks if this instance is currently the leader.
     *
     * @return {@code true} if leader, {@code false} otherwise
     */
    boolean isLeader();

    /**
     * Returns the unique identity of this instance (e.g., pod name, hostname).
     *
     * @return instance identifier
     */
    String getIdentity();
}
