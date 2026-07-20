package com.finmate.infra.kis.parser;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

public final class KisValueParser {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String EMPTY_DATE = "00000000";

    private KisValueParser() {
    }

    public static LocalDate parseRequiredDate(String value, String message) {
        validateRequired(value, message);

        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    public static LocalDate parseNullableDate(String value) {
        if (value == null || value.isBlank() || EMPTY_DATE.equals(value.trim())) {
            return null;
        }

        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    public static BigDecimal parseRequiredBigDecimal(String value, String message) {
        validateRequired(value, message);

        return new BigDecimal(normalizeNumber(value));
    }

    public static BigDecimal parseNullableBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return new BigDecimal(normalizeNumber(value));
    }

    public static BigDecimal parseNullableBigDecimalOrNull(String value) {
        try {
            return parseNullableBigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Optional<BigDecimal> parsePositiveBigDecimal(String value) {
        BigDecimal parsedValue = parseNullableBigDecimalOrNull(value);
        if (parsedValue == null || parsedValue.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        return Optional.of(parsedValue);
    }

    public static Long parseRequiredLong(String value, String message) {
        validateRequired(value, message);

        return Long.parseLong(normalizeNumber(value));
    }

    public static Long parseNullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Long.parseLong(normalizeNumber(value));
    }

    public static Long parseNullableLongOrNull(String value) {
        try {
            return parseNullableLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal applyChangeSign(BigDecimal value, String changeSign) {
        if (value == null) {
            return null;
        }

        return switch (changeSign == null ? "" : changeSign.trim()) {
            case "1", "2" -> value.abs();
            case "3" -> BigDecimal.ZERO;
            case "4", "5" -> value.abs().negate();
            default -> value;
        };
    }

    public static JsonNode firstObject(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isObject()) {
            return node;
        }

        if (node.isArray() && !node.isEmpty()) {
            JsonNode firstNode = node.get(0);
            return firstNode != null && firstNode.isObject() ? firstNode : null;
        }

        return null;
    }

    public static String firstPresentText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }

        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String textValue = value.asText();
                if (textValue != null && !textValue.isBlank()) {
                    return textValue.trim();
                }
            }
        }

        return null;
    }

    public static String firstPresentValue(Map<String, String> values, String... keys) {
        if (values == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    public static boolean parseYesNo(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    public static boolean parseYesNoOrOne(String value) {
        return parseYesNo(value) || "1".equals(value);
    }

    public static String requiredCode(String value, String message) {
        validateRequired(value, message);

        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String optionalCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String requiredText(String value, String message) {
        validateRequired(value, message);

        return value.trim();
    }

    public static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public static String normalizeNumber(String value) {
        return value.trim().replace(",", "");
    }
}
