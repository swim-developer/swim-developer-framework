package com.github.swim_developer.framework.provider.infrastructure.out.queue;

import com.github.swim_developer.framework.domain.exception.QueueProvisioningException;
import com.github.swim_developer.framework.application.port.out.QueueProvisioningStrategy;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionQueuePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Orchestrates provisioning and deprovisioning of the Artemis queue required
 * for a single SWIM subscription.
 *
 * <p>Each subscription owns exactly one queue. Heartbeat messages travel in the
 * same queue as business events, distinguished by AMQP content-type.</p>
 */
@ApplicationScoped
@Slf4j
public class SubscriptionQueueOrchestrator implements SwimSubscriptionQueuePort {

    private final QueueProvisioningStrategy queueProvisioner;
    private final String amqRoleSuffix;

    @Inject
    public SubscriptionQueueOrchestrator(
            QueueProvisioningStrategy queueProvisioner,
            @ConfigProperty(name = "swim.amq.role.suffix") String amqRoleSuffix) {
        this.queueProvisioner = queueProvisioner;
        this.amqRoleSuffix = amqRoleSuffix;
    }

    public void provision(String queueName, String userId) {
        try {
            queueProvisioner.createQueue(queueName);
        } catch (Exception e) {
            log.error("Failed to create queue: {}", queueName, e);
            throw new QueueProvisioningException("Failed to configure queue: " + e.getMessage(), e);
        }

        try {
            queueProvisioner.addSecurityRole(queueName, userId, amqRoleSuffix);
        } catch (Exception e) {
            log.error("Failed to add security role for queue: {}, rolling back", queueName, e);
            removeSilently(queueName);
            throw new QueueProvisioningException("Failed to configure security role: " + e.getMessage(), e);
        }
    }

    public void deprovision(String queueName) {
        removeSilently(queueName);
        removeRoleSilently(queueName);
    }

    private void removeSilently(String queueName) {
        try {
            queueProvisioner.removeQueue(queueName);
        } catch (Exception e) {
            log.warn("Failed to remove queue {}: {}", queueName, e.getMessage());
        }
    }

    private void removeRoleSilently(String queueName) {
        try {
            queueProvisioner.removeSecurityRole(queueName);
        } catch (Exception e) {
            log.warn("Failed to remove security role for {}: {}", queueName, e.getMessage());
        }
    }
}
