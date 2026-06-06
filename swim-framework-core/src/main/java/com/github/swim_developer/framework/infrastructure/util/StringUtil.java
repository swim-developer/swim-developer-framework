package com.github.swim_developer.framework.infrastructure.util;

public final class StringUtil {

    private StringUtil() {
    }

    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean hasValue(String value) {
        return !isNullOrBlank(value);
    }
}
