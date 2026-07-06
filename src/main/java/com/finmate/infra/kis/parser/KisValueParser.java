package com.finmate.infra.kis.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
