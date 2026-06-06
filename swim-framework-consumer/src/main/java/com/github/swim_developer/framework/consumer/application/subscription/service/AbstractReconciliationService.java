package com.github.swim_developer.framework.consumer.application.subscription.service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class AbstractReconciliationService<D, S> {

    public void reconcileCreate(List<D> desiredSubscriptions) {
        for (D desired : desiredSubscriptions) {
            if (!existsLocally(desired)) {
                try {
                    createAndActivate(desired);
                } catch (Exception e) {
                    log.error("Failed to create subscription: {} — {}", describeDesired(desired), ExceptionRootMessage.format(e));
                    log.debug("Full stack trace for failed subscription: {}", describeDesired(desired), e);
                }
            }
        }
    }

    public void reconcileDelete(List<D> desiredSubscriptions) {
        List<S> currentSubscriptions = loadCurrentSubscriptions();
        for (S current : currentSubscriptions) {
            if (!isStillDesired(current, desiredSubscriptions)) {
                try {
                    deactivateAndDelete(current);
                } catch (Exception e) {
                    log.error("Failed to delete subscription: {} — {}", describeCurrent(current), ExceptionRootMessage.format(e));
                    log.debug("Full stack trace for failed subscription deletion: {}", describeCurrent(current), e);
                }
            }
        }
    }

    protected abstract boolean existsLocally(D desired);

    protected abstract void createAndActivate(D desired);

    protected abstract List<S> loadCurrentSubscriptions();

    protected abstract boolean isStillDesired(S current, List<D> desiredSubscriptions);

    protected abstract void deactivateAndDelete(S current);

    protected abstract String describeDesired(D desired);

    protected abstract String describeCurrent(S current);
}
