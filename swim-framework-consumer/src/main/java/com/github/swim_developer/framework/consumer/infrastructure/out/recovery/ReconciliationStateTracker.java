package com.github.swim_developer.framework.consumer.infrastructure.out.recovery;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@ApplicationScoped
public class ReconciliationStateTracker {

    private final AtomicBoolean reconciled = new AtomicBoolean(false);

    public boolean isReconciled() {
        return reconciled.get();
    }

    public void markAsReconciled() {
        if (reconciled.compareAndSet(false, true)) {
            log.info("Reconciliation completed successfully");
        }
    }

    public void reset() {
        reconciled.set(false);
        log.warn("Reconciliation state reset");
    }
}
