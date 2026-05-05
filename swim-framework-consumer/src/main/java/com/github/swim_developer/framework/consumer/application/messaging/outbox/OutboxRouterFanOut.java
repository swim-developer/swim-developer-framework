package com.github.swim_developer.framework.consumer.application.messaging.outbox;

import com.github.swim_developer.framework.application.port.out.SwimOutboxRouter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;

/**
 * Inclusive fan-out dispatcher for EP-3 (SwimOutboxRouter).
 *
 * <p>Collects all {@link SwimOutboxRouter} implementations present on the classpath
 * and dispatches each event to ALL of them in parallel. Routers execute independently:
 * a failure or timeout in one router does NOT affect the others.</p>
 *
 * <p>Each router dispatch is subject to {@code swim.outbox.fanout.timeout-ms} (default 10 s).
 * If a router does not complete within the timeout its future is abandoned and a warning is
 * logged — the main thread is never blocked indefinitely.</p>
 */
@Slf4j
@ApplicationScoped
public class OutboxRouterFanOut {

    private final Instance<SwimOutboxRouter> routerInstances;
    private final ManagedExecutor executor;
    private final long timeoutMs;

    private List<SwimOutboxRouter> routers;

    @Inject
    public OutboxRouterFanOut(@Any Instance<SwimOutboxRouter> routerInstances,
                              ManagedExecutor executor,
                              @ConfigProperty(name = "swim.outbox.fanout.timeout-ms", defaultValue = "10000")
                              long timeoutMs) {
        this.routerInstances = routerInstances;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    @PostConstruct
    void init() {
        routers = StreamSupport.stream(routerInstances.spliterator(), false).toList();
        log.info("OutboxRouterFanOut initialized with {} router(s): {}",
                routers.size(),
                routers.stream().map(r -> r.getClass().getSimpleName()).toList());
    }

    public void route(String messageId, String payload) {
        fanOut("route", messageId, router -> router.route(messageId, payload));
    }

    public void sendToDeadLetterQueue(String messageId, String payload) {
        fanOut("sendToDeadLetterQueue", messageId, router -> router.sendToDeadLetterQueue(messageId, payload));
    }

    private void fanOut(String operation, String messageId, RouterAction action) {
        if (routers.isEmpty()) {
            log.warn("OutboxRouterFanOut: no routers registered — {} for messageId={} dropped", operation, messageId);
            return;
        }

        List<CompletableFuture<Void>> futures = routers.stream()
                .map(router -> CompletableFuture
                        .runAsync(() -> invokeRouter(router, operation, messageId, action), executor)
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            handleFailure(router, operation, messageId, ex);
                            return null;
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void invokeRouter(SwimOutboxRouter router, String operation, String messageId, RouterAction action) {
        String routerName = router.getClass().getSimpleName();
        try {
            action.apply(router);
            log.debug("Router {} completed {} for messageId={}", routerName, operation, messageId);
        } catch (Exception e) {
            log.error("Router {} failed {} for messageId={}: {}", routerName, operation, messageId, e.getMessage(), e);
            throw e;
        }
    }

    private void handleFailure(SwimOutboxRouter router, String operation, String messageId, Throwable ex) {
        String routerName = router.getClass().getSimpleName();
        if (ex instanceof TimeoutException) {
            log.error("Router {} timed out after {}ms during {} for messageId={} — router abandoned",
                    routerName, timeoutMs, operation, messageId);
        } else {
            log.error("Router {} threw unhandled exception during {} for messageId={}: {}",
                    routerName, operation, messageId, ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface RouterAction {
        void apply(SwimOutboxRouter router);
    }
}
