package com.github.swim_developer.framework.domain.exception;

public class XmlSchemaLoadException extends RuntimeException {

    public XmlSchemaLoadException(String message) {
        super(message);
    }

    public XmlSchemaLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
