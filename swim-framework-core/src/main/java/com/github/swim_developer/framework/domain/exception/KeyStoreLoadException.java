package com.github.swim_developer.framework.domain.exception;

public class KeyStoreLoadException extends RuntimeException {

    private final String path;

    public KeyStoreLoadException(String path, String message) {
        super("Failed to load keystore from " + path + ": " + message);
        this.path = path;
    }

    public KeyStoreLoadException(String path, Throwable cause) {
        super("Failed to load keystore from " + path, cause);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
