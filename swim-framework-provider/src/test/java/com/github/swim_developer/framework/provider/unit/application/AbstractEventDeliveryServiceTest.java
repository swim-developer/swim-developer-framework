package com.github.swim_developer.framework.provider.unit.application;

import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SwimSubscription;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.application.subscription.AbstractEventDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractEventDeliveryServiceTest {

    record StubEvent(String id, String payload, String airport) {}

    static class StubSubscription implements SwimSubscription<String> {
        final UUID subscriptionId = UUID.randomUUID();
        final String queue;
        final Predicate<String> filter;

        StubSubscription(String queue, Predicate<String> filter) {
            this.queue = queue;
            this.filter = filter;
        }

        @Override public UUID getSubscriptionId() { return subscriptionId; }
        @Override public String getQueue() { return queue; }
        @Override public QualityOfService getQos() { return QualityOfService.AT_LEAST_ONCE; }
        @Override public SubscriptionStatus getStatus() { return SubscriptionStatus.ACTIVE; }
        @Override public Predicate<String> toFilter() { return filter; }
    }

    private List<String> publishedQueues;
    private List<StubSubscription> subscriptions;

    @BeforeEach
    void setUp() {
        publishedQueues = new ArrayList<>();
        subscriptions = new ArrayList<>();
    }

    private AbstractEventDeliveryService<StubEvent, String, StubSubscription> service() {
        return new AbstractEventDeliveryService<>(Executors.newVirtualThreadPerTaskExecutor()) {
            @Override protected String toFilterableModel(StubEvent e) { return e.airport(); }
            @Override protected String extractPayload(StubEvent e) { return e.payload(); }
            @Override protected String extractEventId(StubEvent e) { return e.id(); }
            @Override protected List<StubSubscription> loadActiveSubscriptions() { return subscriptions; }
            @Override protected void publishToQueue(String queue, String payload, QualityOfService qos, UUID id) {
                publishedQueues.add(queue);
            }
        };
    }

    @Test
    void deliverToMatchingSubscriptions_returnsZeroWhenNoSubscriptions() {
        DeliveryResult result = service().deliverToMatchingSubscriptions(new StubEvent("e1", "<xml/>", "EBBR"));
        assertThat(result.matched()).isZero();
        assertThat(result.delivered()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void deliverToMatchingSubscriptions_returnsZeroWhenNoneMatch() {
        subscriptions.add(new StubSubscription("q1", airport -> airport.equals("LFPG")));
        DeliveryResult result = service().deliverToMatchingSubscriptions(new StubEvent("e1", "<xml/>", "EBBR"));
        assertThat(result.matched()).isZero();
        assertThat(publishedQueues).isEmpty();
    }

    @Test
    void deliverToMatchingSubscriptions_deliversToMatchingSubscriptions() {
        subscriptions.add(new StubSubscription("q1", a -> a.equals("EBBR")));
        subscriptions.add(new StubSubscription("q2", a -> a.equals("LFPG")));
        DeliveryResult result = service().deliverToMatchingSubscriptions(new StubEvent("e1", "<xml/>", "EBBR"));
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.delivered()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(publishedQueues).containsExactly("q1");
    }

    @Test
    void deliverToMatchingSubscriptions_recordsFailedDelivery_whenPublishThrows() {
        subscriptions.add(new StubSubscription("q-fail", a -> true));

        AbstractEventDeliveryService<StubEvent, String, StubSubscription> svc =
                new AbstractEventDeliveryService<>(Executors.newVirtualThreadPerTaskExecutor()) {
                    @Override protected String toFilterableModel(StubEvent e) { return e.airport(); }
                    @Override protected String extractPayload(StubEvent e) { return e.payload(); }
                    @Override protected String extractEventId(StubEvent e) { return e.id(); }
                    @Override protected List<StubSubscription> loadActiveSubscriptions() { return subscriptions; }
                    @Override protected void publishToQueue(String q, String p, QualityOfService qos, UUID id) {
                        throw new RuntimeException("broker unavailable");
                    }
                };

        DeliveryResult result = svc.deliverToMatchingSubscriptions(new StubEvent("e1", "<xml/>", "EBBR"));
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.delivered()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
    }

    @Test
    void deliverToMatchingSubscriptions_deliversToMultipleMatchingSubscriptions() {
        subscriptions.add(new StubSubscription("q1", a -> true));
        subscriptions.add(new StubSubscription("q2", a -> true));
        subscriptions.add(new StubSubscription("q3", a -> false));

        DeliveryResult result = service().deliverToMatchingSubscriptions(new StubEvent("e1", "<xml/>", "EBBR"));

        assertThat(result.matched()).isEqualTo(2);
        assertThat(result.delivered()).isEqualTo(2);
        assertThat(publishedQueues).containsExactlyInAnyOrder("q1", "q2");
    }
}
