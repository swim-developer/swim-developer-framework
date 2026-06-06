package com.github.swim_developer.framework.domain.exception;

public class QueueProvisioningException extends RuntimeException {

    public QueueProvisioningException(String message) {
        super(message);
    }

    public QueueProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
