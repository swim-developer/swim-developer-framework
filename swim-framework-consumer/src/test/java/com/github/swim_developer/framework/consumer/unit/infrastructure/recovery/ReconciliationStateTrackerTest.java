package com.github.swim_developer.framework.consumer.unit.infrastructure.recovery;

import com.github.swim_developer.framework.consumer.infrastructure.out.recovery.ReconciliationStateTracker;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class ReconciliationStateTrackerTest {

    private ReconciliationStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ReconciliationStateTracker();
    }

    @Test
    void isReconciled_returnsFalseInitially() {
        assertThat(tracker.isReconciled()).isFalse();
    }

    @Test
    void markAsReconciled_changesStateToTrue() {
        tracker.markAsReconciled();
        assertThat(tracker.isReconciled()).isTrue();
    }

    @Test
    void markAsReconciled_isIdempotent() {
        tracker.markAsReconciled();
        tracker.markAsReconciled();
        assertThat(tracker.isReconciled()).isTrue();
    }

    @Test
    void reset_changesStateBackToFalse() {
        tracker.markAsReconciled();
        tracker.reset();
        assertThat(tracker.isReconciled()).isFalse();
    }

    @Test
    void reset_onUninitializedTracker_keepsStateFalse() {
        tracker.reset();
        assertThat(tracker.isReconciled()).isFalse();
    }

    @Test
    void fullLifecycle_markThenResetThenMarkAgain() {
        tracker.markAsReconciled();
        assertThat(tracker.isReconciled()).isTrue();

        tracker.reset();
        assertThat(tracker.isReconciled()).isFalse();

        tracker.markAsReconciled();
        assertThat(tracker.isReconciled()).isTrue();
    }
}
