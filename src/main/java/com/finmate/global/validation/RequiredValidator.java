package com.finmate.global.validation;

import com.finmate.exception.BusinessRuleException;

public final class RequiredValidator {
    private RequiredValidator() {
    }

    public static void validateRequired(Object value, String message) {
        if (value == null) {
            throw new BusinessRuleException(message);
        }

        if (value instanceof String string && string.isBlank()) {
            throw new BusinessRuleException(message);
        }
    }
}
