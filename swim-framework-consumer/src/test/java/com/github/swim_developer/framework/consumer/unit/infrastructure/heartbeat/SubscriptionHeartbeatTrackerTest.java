package com.github.swim_developer.framework.consumer.unit.infrastructure.heartbeat;

import com.github.swim_developer.framework.consumer.infrastructure.out.heartbeat.SubscriptionHeartbeatTracker;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SubscriptionHeartbeatTrackerTest {

    private SubscriptionHeartbeatTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SubscriptionHeartbeatTracker();
    }

    @Test
    void recordHeartbeat_storesHeartbeatAndIsRetrievable() {
        SubscriptionHeartbeat hb = heartbeat("ACTIVE");

        tracker.recordHeartbeat("sub-1", hb);

        assertThat(tracker.get("sub-1")).isSameAs(hb);
    }

    @Test
    void get_returnsNull_forUnknownSubscription() {
        assertThat(tracker.get("non-existent")).isNull();
    }

    @Test
    void isEmpty_returnsTrueWhenNoHeartbeatsRecorded() {
        assertThat(tracker.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_returnsFalse_afterHeartbeatRecorded() {
        tracker.recordHeartbeat("sub-1", heartbeat("ACTIVE"));

        assertThat(tracker.isEmpty()).isFalse();
    }

    @Test
    void remove_clearsHeartbeatAndRegistration() {
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", heartbeat("ACTIVE"));

        tracker.remove("sub-1");

        assertThat(tracker.get("sub-1")).isNull();
        assertThat(tracker.getSubscriptionsWithoutHeartbeat()).doesNotContainKey("sub-1");
    }

    @Test
    void remove_doesNotFailForUnknownSubscription() {
        tracker.remove("ghost");

        assertThat(tracker.isEmpty()).isTrue();
    }

    @Test
    void registerSubscription_isIdempotent() {
        tracker.registerSubscription("sub-1");
        Instant first = tracker.getSubscriptionsWithoutHeartbeat().get("sub-1");

        tracker.registerSubscription("sub-1");
        Instant second = tracker.getSubscriptionsWithoutHeartbeat().get("sub-1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void getSubscriptionsWithoutHeartbeat_returnsOnlyUnrespondedSubscriptions() {
        tracker.registerSubscription("sub-missing");
        tracker.registerSubscription("sub-active");
        tracker.recordHeartbeat("sub-active", heartbeat("ACTIVE"));

        Map<String, Instant> result = tracker.getSubscriptionsWithoutHeartbeat();

        assertThat(result).containsKey("sub-missing").doesNotContainKey("sub-active");
    }

    @Test
    void getSubscriptionsWithoutHeartbeat_returnsEmpty_whenAllHaveHeartbeats() {
        tracker.registerSubscription("sub-1");
        tracker.recordHeartbeat("sub-1", heartbeat("ACTIVE"));

        assertThat(tracker.getSubscriptionsWithoutHeartbeat()).isEmpty();
    }

    @Test
    void entries_containsAllRecordedHeartbeats() {
        SubscriptionHeartbeat hb1 = heartbeat("ACTIVE");
        SubscriptionHeartbeat hb2 = heartbeat("PAUSED");
        tracker.recordHeartbeat("sub-1", hb1);
        tracker.recordHeartbeat("sub-2", hb2);

        assertThat(tracker.entries())
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("sub-1", "sub-2");
    }

    @Test
    void resetRegistration_updatesRegistrationTimestamp() {
        tracker.registerSubscription("sub-1");
        Instant before = tracker.getSubscriptionsWithoutHeartbeat().get("sub-1");

        Instant deadline = Instant.now().plusMillis(5);
        await().atMost(Duration.ofSeconds(2)).until(() -> Instant.now().isAfter(deadline));
        tracker.resetRegistration("sub-1");
        Instant after = tracker.getSubscriptionsWithoutHeartbeat().get("sub-1");

        assertThat(after).isAfter(before);
    }

    @Test
    void recordHeartbeat_overwritesPreviousHeartbeat() {
        tracker.recordHeartbeat("sub-1", heartbeat("ACTIVE"));
        SubscriptionHeartbeat updated = heartbeat("PAUSED");

        tracker.recordHeartbeat("sub-1", updated);

        assertThat(tracker.get("sub-1").subscriptionState()).isEqualTo("PAUSED");
    }

    private static SubscriptionHeartbeat heartbeat(String state) {
        Instant now = Instant.now();
        return new SubscriptionHeartbeat(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                state,
                "HEALTHY",
                now,
                now.plusSeconds(30),
                1L
        );
    }
}
