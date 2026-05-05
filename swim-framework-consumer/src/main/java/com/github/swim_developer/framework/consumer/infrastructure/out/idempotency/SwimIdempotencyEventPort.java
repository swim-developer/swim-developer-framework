package com.github.swim_developer.framework.consumer.infrastructure.out.idempotency;

import java.time.Instant;
import java.util.List;

public interface SwimIdempotencyEventPort {

    boolean existsBySubscriptionAndContentHash(String subscriptionId, String contentHash);

    List<String> findRecentCacheKeys(Instant since, int limit);
}
