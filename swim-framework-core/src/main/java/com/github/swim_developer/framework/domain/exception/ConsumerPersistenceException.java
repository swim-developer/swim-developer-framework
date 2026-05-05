package com.github.swim_developer.framework.domain.exception;

public class ConsumerPersistenceException extends RuntimeException {

    public ConsumerPersistenceException(String message) {
        super(message);
    }

    public ConsumerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
