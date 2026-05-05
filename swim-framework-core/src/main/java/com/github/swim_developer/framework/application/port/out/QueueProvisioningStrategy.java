package com.github.swim_developer.framework.application.port.out;

/**
 * Strategy for provisioning AMQP queues and security roles in message brokers.
 *
 * <h2>Purpose</h2>
 * <p>
 * SWIM providers must dynamically create queues for subscriptions and restrict access
 * to the subscription owner. The provisioning mechanism varies by deployment environment:
 * </p>
 * <ul>
 *   <li><strong>Kubernetes/OpenShift:</strong> Provisions via Secrets detected by AMQ Broker Operator</li>
 *   <li><strong>Local development:</strong> Provisions via Artemis Jolokia REST API (JMX over HTTP)</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>
 * Each queue is protected by role-based access control (RBAC):
 * </p>
 * <ul>
 *   <li><strong>Consume permission:</strong> Restricted to subscription owner's role (e.g., {@code username-amq})</li>
 *   <li><strong>Send permission:</strong> Restricted to {@code admin} role (provider publishes messages)</li>
 *   <li><strong>Manage permission:</strong> Restricted to {@code admin} role</li>
 * </ul>
 *
 * <h2>Implementation Selection</h2>
 * <p>
 * Implementations are selected automatically via Quarkus build profiles:
 * </p>
 * <pre>
 * {@literal @}ApplicationScoped
 * {@literal @}IfBuildProfile("prod")
 * public class KubernetesQueueProvisioner implements QueueProvisioningStrategy { }
 *
 * {@literal @}ApplicationScoped
 * {@literal @}UnlessBuildProfile("prod")
 * public class ArtemisJmxQueueProvisioner implements QueueProvisioningStrategy { }
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Inject
 * QueueProvisioningStrategy provisioner;
 *
 * // Create queue and restrict access to owner
 * provisioner.createQueue("DNOTAM.user1.abc123");
 * provisioner.addSecurityRole("DNOTAM.user1.abc123", "user1", "-amq");
 *
 * // Later: cleanup when subscription deleted
 * provisioner.removeSecurityRole("DNOTAM.user1.abc123");
 * provisioner.removeQueue("DNOTAM.user1.abc123");
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>
 * Implementations must throw {@link RuntimeException} on failure. The caller
 * ({@link com.github.swim_developer.framework.provider.application.subscription.AbstractProviderSubscriptionService})
 * performs rollback by calling {@code removeQueue()} and {@code removeSecurityRole()}.
 * </p>
 *
 * @since 1.0.0
 * @see com.github.swim_developer.framework.provider.infrastructure.out.queue.KubernetesQueueProvisioner
 * @see com.github.swim_developer.framework.provider.infrastructure.out.queue.ArtemisJmxQueueProvisioner
 */
public interface QueueProvisioningStrategy {

    /**
     * Creates a new durable ANYCAST queue in the message broker.
     *
     * <p>
     * The queue must be created with:
     * </p>
     * <ul>
     *   <li>Routing type: {@code ANYCAST}</li>
     *   <li>Durable: {@code true} (survive broker restart)</li>
     *   <li>Address name = Queue name (1:1 mapping)</li>
     * </ul>
     *
     * @param queueName the fully qualified queue name (e.g., {@code DNOTAM.user1.abc123})
     * @throws RuntimeException if queue creation fails
     */
    void createQueue(String queueName);

    /**
     * Adds security role configuration to restrict queue access to the subscription owner.
     *
     * <p>
     * Security settings must grant:
     * </p>
     * <ul>
     *   <li><strong>Consume + Browse:</strong> {@code username + roleSuffix} (e.g., {@code user1-amq})</li>
     *   <li><strong>Send + Manage:</strong> {@code admin} role only</li>
     * </ul>
     *
     * @param queueName the queue name to protect (e.g., {@code DNOTAM.user1.abc123})
     * @param username the subscription owner's username (e.g., {@code user1})
     * @param roleSuffix the role suffix appended to username (e.g., {@code -amq})
     * @throws RuntimeException if security configuration fails
     */
    void addSecurityRole(String queueName, String username, String roleSuffix);

    /**
     * Removes the queue from the message broker.
     *
     * <p>
     * This method must be idempotent. If the queue does not exist, no exception should be thrown.
     * </p>
     *
     * @param queueName the queue name to remove
     * @throws RuntimeException if queue removal fails (except for "queue not found" case)
     */
    void removeQueue(String queueName);

    /**
     * Removes security role configuration for the queue.
     *
     * <p>
     * This method must be idempotent. If the security settings do not exist, no exception should be thrown.
     * </p>
     *
     * @param queueName the queue name whose security settings should be removed
     * @throws RuntimeException if security removal fails (except for "settings not found" case)
     */
    void removeSecurityRole(String queueName);
}
