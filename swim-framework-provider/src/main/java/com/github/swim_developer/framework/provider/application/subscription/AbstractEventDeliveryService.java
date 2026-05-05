package com.github.swim_developer.framework.provider.application.subscription;

import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.FailedDeliveryInfo;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import com.github.swim_developer.framework.domain.model.SwimSubscription;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public abstract class AbstractEventDeliveryService<E, F, S extends SwimSubscription<F>> {

    private static final int MAX_PARALLELISM = 50;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final ExecutorService executor;

    protected AbstractEventDeliveryService() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    protected AbstractEventDeliveryService(ExecutorService executor) {
        this.executor = executor;
    }

    public DeliveryResult deliverToMatchingSubscriptions(E eventEntity) {
        F filterable = toFilterableModel(eventEntity);
        String payload = extractPayload(eventEntity);
        List<S> subscriptions = loadActiveSubscriptions();

        List<S> matched = subscriptions.stream()
                .filter(s -> s.toFilter().test(filterable))
                .toList();

        if (matched.isEmpty()) {
            log.info("Delivery completed - Matched: 0, Delivered: 0, Failed: 0");
            return new DeliveryResult(0, 0, 0);
        }

        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<FailedDeliveryInfo> failures = Collections.synchronizedList(new ArrayList<>());

        List<List<S>> batches = partition(matched, MAX_PARALLELISM);

        for (List<S> batch : batches) {
            CompletableFuture<?>[] futures = batch.stream()
                    .map(subscription -> CompletableFuture.runAsync(
                            () -> deliverToSubscription(eventEntity, payload, subscription, delivered, failed, failures),
                            executor
                    ))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
        }

        log.info("Delivery completed - Matched: {}, Delivered: {}, Failed: {}",
                matched.size(), delivered.get(), failed.get());
        return new DeliveryResult(matched.size(), delivered.get(), failed.get(), failures);
    }

    private void deliverToSubscription(E eventEntity, String payload, S subscription,
                                       AtomicInteger delivered, AtomicInteger failed,
                                       List<FailedDeliveryInfo> failures) {
        try {
            publishToQueue(subscription.getQueue(), payload,
                    subscription.getQos(), subscription.getSubscriptionId());
            delivered.incrementAndGet();
            onDeliverySuccess(eventEntity, subscription);
        } catch (Exception e) {
            failed.incrementAndGet();
            log.error("Delivery failed - Queue: {}, Subscription: {}, Error: {}",
                    subscription.getQueue(), subscription.getSubscriptionId(), e.getMessage());
            failures.add(new FailedDeliveryInfo(
                    subscription.getSubscriptionId(),
                    subscription.getQueue(),
                    e.getMessage()
            ));
            persistFailedDelivery(eventEntity, subscription, e);
            onDeliveryFailure(eventEntity, subscription, e);
        }
    }

    private void persistFailedDelivery(E eventEntity, S subscription, Exception e) {
        getFailedDeliveryStore().ifPresent(store -> {
            try {
                String errorMsg = truncateError(e.getMessage(), MAX_ERROR_LENGTH);
                store.persist(store.createRecord(
                        extractEventId(eventEntity),
                        subscription.getSubscriptionId(),
                        subscription.getQueue(),
                        errorMsg));
            } catch (Exception persistError) {
                log.error("Failed to persist failed delivery record - Queue: {}, Subscription: {}",
                        subscription.getQueue(), subscription.getSubscriptionId(), persistError);
            }
        });
    }

    private String truncateError(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    protected abstract F toFilterableModel(E entity);

    protected abstract String extractPayload(E entity);

    protected abstract String extractEventId(E entity);

    protected abstract List<S> loadActiveSubscriptions();

    protected abstract void publishToQueue(String queue, String payload,
                                           QualityOfService qos, UUID subscriptionId);

    protected Optional<FailedDeliveryStore<SwimFailedDelivery>> getFailedDeliveryStore() {
        return Optional.empty();
    }

    protected void onDeliverySuccess(E entity, S subscription) {
    }

    protected void onDeliveryFailure(E entity, S subscription, Exception e) {
    }
}
