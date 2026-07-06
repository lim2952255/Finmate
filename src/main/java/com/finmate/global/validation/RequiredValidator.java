package com.finmate.global.validation;

public final class RequiredValidator {
    private RequiredValidator() {
    }

    public static void validateRequired(Object value, String message) {
        if (value == null) {
            throw new RuntimeException(message);
        }

        if (value instanceof String string && string.isBlank()) {
            throw new RuntimeException(message);
        }
    }
}
