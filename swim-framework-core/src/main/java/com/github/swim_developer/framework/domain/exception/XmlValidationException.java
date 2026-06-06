package com.github.swim_developer.framework.domain.exception;

public class XmlValidationException extends RuntimeException {

    public XmlValidationException(String message) {
        super(message);
    }

    public XmlValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
