package com.finmate.global.validation;

import java.math.BigDecimal;

public final class NumericValidator {
    private NumericValidator() {
    }

    public static void validatePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new RuntimeException(message);
        }
    }

    public static void validatePositive(BigDecimal value, String requiredMessage, String positiveMessage) {
        if (value == null) {
            throw new RuntimeException(requiredMessage);
        }

        if (value.signum() <= 0) {
            throw new RuntimeException(positiveMessage);
        }
    }

    public static void validateNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new RuntimeException(message);
        }
    }

    public static void validateNullableNonNegative(BigDecimal value, String message) {
        if (value == null) {
            return;
        }

        validateNonNegative(value, message);
    }

    public static void validateNonNegative(Long value, String message) {
        if (value == null || value < 0) {
            throw new RuntimeException(message);
        }
    }

    public static void validateNullableNonNegative(Long value, String message) {
        if (value == null) {
            return;
        }

        validateNonNegative(value, message);
    }
}
