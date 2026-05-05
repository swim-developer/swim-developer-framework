package com.github.swim_developer.framework.provider.application.subscription;

import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionQueuePort;
import com.github.swim_developer.framework.application.port.out.SwimSecurityContext;
import com.github.swim_developer.framework.domain.model.SwimSubscriptionEntity;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionRepository;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Template for SWIM subscription lifecycle management.
 *
 * <p>Each concrete service only needs to implement six domain-specific hooks:
 * {@link #getQueuePrefix()}, {@link #getDefaultTtl()}, {@link #getRequestedQueueName(Object)},
 * {@link #calculateHash(Object, String)}, {@link #createEntity(Object, String, String, String)},
 * {@link #mapToResponse(SwimSubscriptionEntity)}, and {@link #validateRequest(Object, String)}.
 * All persistence and queue-provisioning concerns are handled via injected collaborators.</p>
 *
 * @param <E> entity type — must implement {@link SwimSubscriptionEntity}
 * @param <R> request DTO type
 * @param <S> response DTO type
 */
@Slf4j
public abstract class AbstractProviderSubscriptionService<E extends SwimSubscriptionEntity, R, S> {

    private final SwimSecurityContext securityContext;
    private final SwimSubscriptionQueuePort queueOrchestrator;
    private final SwimSubscriptionRepository<E> subscriptionRepository;

    @Inject
    protected AbstractProviderSubscriptionService(
            SwimSecurityContext securityContext,
            SwimSubscriptionQueuePort queueOrchestrator,
            SwimSubscriptionRepository<E> subscriptionRepository) {
        this.securityContext = securityContext;
        this.queueOrchestrator = queueOrchestrator;
        this.subscriptionRepository = subscriptionRepository;
    }

    protected abstract String getQueuePrefix();

    protected abstract Duration getDefaultTtl();

    protected abstract String getRequestedQueueName(R request);

    protected abstract String calculateHash(R request, String userId);

    protected abstract E createEntity(R request, String userId, String queueName, String hash);

    protected abstract S mapToResponse(E entity);

    protected abstract void validateRequest(R request, String userId);

    @Transactional
    @Retry(maxRetries = 2, delay = 500, retryOn = {OptimisticLockException.class})
    @Timeout(value = 30, unit = ChronoUnit.SECONDS)
    public S createSubscription(R request) {
        String userId = securityContext.getUsername();
        log.info("Creating subscription for user: {}", userId);

        validateRequest(request, userId);
        securityContext.validateAmqRole(userId);

        String hash = calculateHash(request, userId);

        Optional<S> existing = subscriptionRepository.findByHash(hash).map(this::mapToResponse);
        if (existing.isPresent()) {
            log.info("Duplicate subscription detected, returning existing. Hash: {}", hash);
            return existing.get();
        }

        QueueResolution resolution = resolveQueueName(getRequestedQueueName(request), userId);
        String queueName = resolution.queueName() != null
                ? resolution.queueName()
                : generateQueueName(userId);

        E entity = createEntity(request, userId, queueName, hash);
        entity.setStatus(SubscriptionStatus.PAUSED);

        if (!resolution.reuseExisting()) {
            queueOrchestrator.provision(queueName, userId);
        } else {
            log.info("Reusing existing queue for user: {} - Queue: {}", userId, queueName);
        }

        try {
            subscriptionRepository.persist(entity);
        } catch (Exception e) {
            log.error("Failed to persist subscription, rolling back queue resources: {}", e.getMessage());
            if (!resolution.reuseExisting()) {
                queueOrchestrator.deprovision(queueName);
            }
            throw e;
        }

        log.info("Subscription created - ID: {}, Queue: {}, User: {}, ReuseQueue: {}",
                entity.getSubscriptionId(), queueName, userId, resolution.reuseExisting());

        return mapToResponse(entity);
    }

    @Retry(maxRetries = 3, delay = 1000)
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 60000)
    public S getSubscription(UUID subscriptionId) {
        E entity = findEntityByIdOrThrow(subscriptionId);
        ensureSubscriptionOwnedByCurrentUser(entity);
        return mapToResponse(entity);
    }

    @Retry(maxRetries = 3, delay = 1000)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 60000)
    public List<S> listSubscriptions() {
        String userId = securityContext.getUsername();
        return subscriptionRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    @Retry(maxRetries = 2, delay = 500, retryOn = {OptimisticLockException.class})
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public S updateStatus(UUID subscriptionId, SubscriptionStatus newStatus) {
        E entity = findEntityByIdOrThrow(subscriptionId);
        ensureSubscriptionOwnedByCurrentUser(entity);
        validateNotDeleted(entity);

        entity.setStatus(newStatus);
        entity.setUpdatedAt(Instant.now());
        subscriptionRepository.persist(entity);

        log.info("Updated subscription {} to status {}", subscriptionId, newStatus);
        return mapToResponse(entity);
    }

    @Transactional
    @Retry(maxRetries = 2, delay = 500, retryOn = {OptimisticLockException.class})
    @Timeout(value = 15, unit = ChronoUnit.SECONDS)
    public void deleteSubscription(UUID subscriptionId) {
        log.info("Deleting subscription: {}", subscriptionId);
        E entity = findEntityByIdOrThrow(subscriptionId);
        ensureSubscriptionOwnedByCurrentUser(entity);
        validateNotDeleted(entity);

        entity.setStatus(SubscriptionStatus.DELETED);
        entity.setUpdatedAt(Instant.now());
        subscriptionRepository.persist(entity);

        log.info("Deleted subscription {}", subscriptionId);
    }

    @Transactional
    @Retry(maxRetries = 2, delay = 500, retryOn = {OptimisticLockException.class})
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public S renewSubscription(UUID subscriptionId, Duration extensionTtl) {
        log.info("Renewing subscription: {}", subscriptionId);
        E entity = findEntityByIdOrThrow(subscriptionId);
        ensureSubscriptionOwnedByCurrentUser(entity);
        validateNotDeleted(entity);

        SubscriptionStatus status = entity.getStatus();
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAUSED) {
            throw new BadRequestException("Cannot renew subscription with status: " + status);
        }

        Instant now = Instant.now();
        Duration ttl = extensionTtl != null ? extensionTtl : getDefaultTtl();
        entity.setSubscriptionEnd(now.plus(ttl));
        entity.setUpdatedAt(now);
        subscriptionRepository.persist(entity);

        log.info("Renewed subscription {} with new end: {}", subscriptionId, entity.getSubscriptionEnd());
        return mapToResponse(entity);
    }

    @Transactional
    public void terminateSubscription(UUID subscriptionId) {
        log.info("Terminating subscription due to expiration: {}", subscriptionId);
        E entity = findEntityByIdOrThrow(subscriptionId);

        entity.setStatus(SubscriptionStatus.TERMINATED);
        entity.setUpdatedAt(Instant.now());
        subscriptionRepository.persist(entity);

        log.info("Terminated subscription {} due to expiration", subscriptionId);
    }

    @Transactional
    public void purgeSubscription(UUID subscriptionId) {
        log.info("Purging subscription: {}", subscriptionId);
        E entity = findEntityByIdOrThrow(subscriptionId);

        queueOrchestrator.deprovision(entity.getQueue());
        subscriptionRepository.delete(entity);

        log.info("Purged subscription {} and cleaned up all resources", subscriptionId);
    }

    protected QueueResolution resolveQueueName(String requestedQueueName, String username) {
        if (requestedQueueName == null || requestedQueueName.isBlank()) {
            return new QueueResolution(null, false);
        }

        var existing = subscriptionRepository.findActiveOrPausedByQueueAndUser(requestedQueueName, username);
        if (existing.isPresent()) {
            log.info("Queue {} belongs to user {}, reusing", requestedQueueName, username);
            return new QueueResolution(requestedQueueName, true);
        }

        if (subscriptionRepository.existsActiveOrPausedByQueue(requestedQueueName)) {
            log.warn("Queue {} belongs to another user, ignoring", requestedQueueName);
            return new QueueResolution(null, false);
        }

        if (isValidQueueNameFormat(requestedQueueName, username)) {
            return new QueueResolution(requestedQueueName, false);
        }

        log.warn("Queue {} has invalid format for user {}, generating new", requestedQueueName, username);
        return new QueueResolution(null, false);
    }

    protected boolean isValidQueueNameFormat(String queueName, String username) {
        if (!queueName.startsWith(getQueuePrefix())) {
            return false;
        }
        String[] parts = queueName.split("-");
        if (parts.length < 3) {
            return false;
        }
        return queueName.contains("-" + username + "-");
    }

    protected String generateQueueName(String userId) {
        return getQueuePrefix() + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void validateNotDeleted(E entity) {
        if (entity.getStatus() == SubscriptionStatus.DELETED) {
            log.warn("Attempted operation on DELETED subscription: {}", entity.getSubscriptionId());
            throw new BadRequestException(
                    "Subscription is in DELETED state and is immutable. No further operations are allowed.");
        }
    }

    protected E findEntityByIdOrThrow(UUID subscriptionId) {
        return subscriptionRepository.findEntityById(subscriptionId)
                .orElseThrow(() -> {
                    log.warn("Subscription not found: {}", subscriptionId);
                    return new NotFoundException("Subscription not found with ID: " + subscriptionId);
                });
    }

    protected void ensureSubscriptionOwnedByCurrentUser(E entity) {
        String currentUser = securityContext.getUsername();
        String owner = entity.getUserId();
        if (owner == null || !owner.equals(currentUser)) {
            log.warn("Denied access to subscription {} for authenticated user {}",
                    entity.getSubscriptionId(), currentUser);
            throw new ForbiddenException("Access denied");
        }
    }

    protected record QueueResolution(String queueName, boolean reuseExisting) {}
}
