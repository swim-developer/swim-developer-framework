package com.github.swim_developer.framework.infrastructure.out.messaging;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ReconciliationResult(int desired, int succeeded, int failed, int skipped) {

    public boolean isFullyReconciled() {
        return failed == 0 && desired > 0;
    }

    public boolean hasFailures() {
        return failed > 0;
    }

    public static ReconciliationResult empty() {
        return new ReconciliationResult(0, 0, 0, 0);
    }
}
