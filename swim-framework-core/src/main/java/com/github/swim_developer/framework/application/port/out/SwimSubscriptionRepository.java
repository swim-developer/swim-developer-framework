package com.github.swim_developer.framework.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimSubscriptionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI for the subscription persistence operations required by
 * {@code AbstractProviderSubscriptionService}.
 *
 * <p>Replaces the seven abstract persistence methods that previously forced
 * each subclass to delegate one-liners to its own repository field.</p>
 *
 * @param <E> the subscription entity type, must extend {@link SwimSubscriptionEntity}
 */
public interface SwimSubscriptionRepository<E extends SwimSubscriptionEntity> {

    void persist(E entity);

    void delete(E entity);

    Optional<E> findEntityById(UUID id);

    Optional<E> findByHash(String hash);

    Optional<E> findActiveOrPausedByQueueAndUser(String queue, String userId);

    boolean existsActiveOrPausedByQueue(String queue);

    List<E> findByUserId(String userId);
}
