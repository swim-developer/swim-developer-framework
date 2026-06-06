package com.github.swim_developer.framework.domain.exception;

public class ConsumerValidationException extends RuntimeException {

    public ConsumerValidationException(String message) {
        super(message);
    }

    public ConsumerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
