package com.finmate.global.format;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public final class DisplayFormatUtils {
    private static final String EMPTY_DISPLAY_VALUE = "-";

    private DisplayFormatUtils() {
    }

    public static String formatDecimal(BigDecimal value, int fractionDigits) {
        return formatDecimal(value, 0, fractionDigits);
    }

    public static String formatFixedDecimal(BigDecimal value, int fractionDigits) {
        return formatDecimal(value, fractionDigits, fractionDigits);
    }

    public static String formatDecimal(BigDecimal value, int minimumFractionDigits, int maximumFractionDigits) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(minimumFractionDigits);
        formatter.setMaximumFractionDigits(maximumFractionDigits);
        return formatter.format(value);
    }

    public static String formatSignedDecimal(BigDecimal value, int fractionDigits) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return sign(value) + formatDecimal(value.abs(), fractionDigits);
    }

    public static String formatSignedFixedDecimal(BigDecimal value, int fractionDigits) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return sign(value) + formatFixedDecimal(value.abs(), fractionDigits);
    }

    public static String formatSignedPercent(BigDecimal value, int fractionDigits) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return formatSignedFixedDecimal(value, fractionDigits) + "%";
    }

    public static String formatInteger(Long value) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return NumberFormat.getIntegerInstance(Locale.KOREA).format(value);
    }

    public static String formatDate(TemporalAccessor value, DateTimeFormatter formatter) {
        if (value == null) {
            return EMPTY_DISPLAY_VALUE;
        }

        return formatter.format(value);
    }

    public static String formatCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String sign(BigDecimal value) {
        if (value.signum() > 0) {
            return "+";
        }

        if (value.signum() < 0) {
            return "-";
        }

        return "";
    }
}
