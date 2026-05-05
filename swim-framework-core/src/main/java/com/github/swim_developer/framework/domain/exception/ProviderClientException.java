package com.github.swim_developer.framework.domain.exception;

public class ProviderClientException extends RuntimeException {

    public ProviderClientException(String message) {
        super(message);
    }

    public ProviderClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
