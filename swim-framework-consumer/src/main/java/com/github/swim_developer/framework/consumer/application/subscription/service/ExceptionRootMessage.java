package com.github.swim_developer.framework.consumer.application.subscription.service;

public final class ExceptionRootMessage {

    private ExceptionRootMessage() {
    }

    public static String format(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
