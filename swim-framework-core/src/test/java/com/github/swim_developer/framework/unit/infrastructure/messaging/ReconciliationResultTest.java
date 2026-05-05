package com.github.swim_developer.framework.unit.infrastructure.messaging;

import com.github.swim_developer.framework.infrastructure.out.messaging.ReconciliationResult;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class ReconciliationResultTest {

    @Test
    void isFullyReconciled_returnsTrueWhenNoFailuresAndDesiredPositive() {
        var result = new ReconciliationResult(3, 3, 0, 0);
        assertThat(result.isFullyReconciled()).isTrue();
    }

    @Test
    void isFullyReconciled_returnsFalseWhenHasFailures() {
        var result = new ReconciliationResult(3, 2, 1, 0);
        assertThat(result.isFullyReconciled()).isFalse();
    }

    @Test
    void isFullyReconciled_returnsFalseWhenDesiredIsZero() {
        var result = new ReconciliationResult(0, 0, 0, 0);
        assertThat(result.isFullyReconciled()).isFalse();
    }

    @Test
    void hasFailures_returnsTrueWhenFailedGreaterThanZero() {
        var result = new ReconciliationResult(2, 1, 1, 0);
        assertThat(result.hasFailures()).isTrue();
    }

    @Test
    void hasFailures_returnsFalseWhenNoFailures() {
        var result = new ReconciliationResult(2, 2, 0, 0);
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void empty_returnsAllZeroResult() {
        var result = ReconciliationResult.empty();
        assertThat(result.desired()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.isFullyReconciled()).isFalse();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void recordEquality_twoIdenticalResultsAreEqual() {
        var a = new ReconciliationResult(5, 3, 1, 1);
        var b = new ReconciliationResult(5, 3, 1, 1);
        assertThat(a).isEqualTo(b);
    }
}
