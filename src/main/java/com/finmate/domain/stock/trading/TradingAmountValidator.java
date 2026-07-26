package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

public final class TradingAmountValidator {
    public static final int QUANTITY_SCALE = 6;
    // 최소 만료기한: 5분, 최대 만료기한: 30일
    public static final Duration MIN_EXPIRATION_LEAD_TIME = Duration.ofMinutes(5);
    public static final Duration MAX_EXPIRATION_LEAD_TIME = Duration.ofDays(30);

    public static void validatePositiveQuantity(BigDecimal quantity) {
        validatePositive(quantity, "수량은 필수입니다.", "수량은 0보다 커야 합니다.");
    }

    public static void validatePositivePrice(CurrencyCode currencyCode, BigDecimal price, String errorMessage) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        validateRequired(price, errorMessage);
        currencyCode.validateAmountScale(price);
        validatePositive(price, errorMessage);
    }

    public static void validateOrderPrice(StockOrderType orderType,
                                          CurrencyCode currencyCode,
                                          BigDecimal orderPrice) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        if (orderType == StockOrderType.MARKET) {
            return;
        }

        validatePositivePrice(currencyCode, orderPrice, "지정가 주문 가격은 0보다 커야 합니다.");
    }

    // 일반주문의 만료기한 검증
    public static void validateOrderExpiration(StockOrderType orderType, LocalDateTime expiresAt) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        if (orderType == StockOrderType.MARKET) {
            return;
        }

        validateFutureExpiration(
                expiresAt,
                "지정가 주문 만료시각은 필수입니다.",
                "지정가 주문 만료시각은 현재 시각 이후여야 합니다.");
    }

    // 예약주문의 만료기한 검증
    public static void validateReservationExpiration(
            LocalDateTime expiresAt
    ) {
        validateFutureExpiration(
                expiresAt,
                "예약 주문 만료시각은 필수입니다.",
                "예약 주문 만료시각은 현재 시각 이후여야 합니다.");
    }

    public static void validateOrderSubmissionExpiration(StockOrderType orderType,
                                                         LocalDateTime expiresAt) {
        validateOrderSubmissionExpiration(orderType, expiresAt, LocalDateTime.now());
    }

    static void validateOrderSubmissionExpiration(StockOrderType orderType,
                                                  LocalDateTime expiresAt,
                                                  LocalDateTime now) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        if (orderType == StockOrderType.MARKET) {
            return;
        }

        validateFutureExpiration(
                expiresAt,
                "지정가 주문 만료시각은 필수입니다.",
                "지정가 주문 만료시각은 현재 시각 이후여야 합니다.",
                now);
        validateExpirationWindow(expiresAt, now);
    }

    public static void validateReservationSubmissionExpiration(LocalDateTime expiresAt) {
        validateReservationSubmissionExpiration(expiresAt, LocalDateTime.now());
    }

    static void validateReservationSubmissionExpiration(LocalDateTime expiresAt,
                                                        LocalDateTime now) {
        validateFutureExpiration(
                expiresAt,
                "예약 주문 만료시각은 필수입니다.",
                "예약 주문 만료시각은 현재 시각 이후여야 합니다.",
                now);
        validateExpirationWindow(expiresAt, now);
    }

    private static void validateFutureExpiration(
            LocalDateTime expiresAt,
            String requiredMessage,
            String pastMessage
    ) {
        validateFutureExpiration(expiresAt, requiredMessage, pastMessage, LocalDateTime.now());
    }

    private static void validateFutureExpiration(
            LocalDateTime expiresAt,
            String requiredMessage,
            String pastMessage,
            LocalDateTime now
    ) {
        validateRequired(expiresAt, requiredMessage);
        validateRequired(now, "현재 시각은 필수입니다.");

        if (!expiresAt.isAfter(now)) {
            throw new RuntimeException(pastMessage);
        }
    }

    // 주문의 만료기한을 검증한다.
    private static void validateExpirationWindow(LocalDateTime expiresAt, LocalDateTime now) {
        if (expiresAt.isBefore(now.plus(MIN_EXPIRATION_LEAD_TIME))) {
            throw new RuntimeException("주문 만료시각은 접수 시각으로부터 최소 5분 이후여야 합니다.");
        }
        if (expiresAt.isAfter(now.plus(MAX_EXPIRATION_LEAD_TIME))) {
            throw new RuntimeException("주문 만료시각은 접수 시각으로부터 최대 30일 이내여야 합니다.");
        }
    }

    public static void validateCurrencyAmounts(CurrencyCode currencyCode, BigDecimal... amounts) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        for (BigDecimal amount : amounts) {
            validateRequired(amount, "금액은 필수입니다.");
            currencyCode.validateAmountScale(amount);
        }
    }

    public static BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public static BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    public static BigDecimal normalizeRequiredQuantity(BigDecimal quantity) {
        validatePositiveQuantity(quantity);

        return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalizeOrderPrice(CurrencyCode currencyCode,
                                                 StockOrderType orderType,
                                                 BigDecimal orderPrice) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        if (orderType == StockOrderType.MARKET) {
            return null;
        }

        validatePositivePrice(currencyCode, orderPrice, "지정가 주문 가격은 필수입니다.");
        return orderPrice;
    }

    public static BigDecimal normalizePositivePrice(CurrencyCode currencyCode, BigDecimal price, String message) {
        validatePositivePrice(currencyCode, price, message);
        return price;
    }

    public static BigDecimal normalizeCurrencyAmount(CurrencyCode currencyCode,
                                                     BigDecimal amount,
                                                     RoundingMode roundingMode) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        validateRequired(amount, "금액은 필수입니다.");

        return amount.setScale(currencyCode.getFractionDigits(), roundingMode);
    }

    public static BigDecimal calculateAmount(CurrencyCode currencyCode,
                                             BigDecimal price,
                                             BigDecimal quantity,
                                             RoundingMode roundingMode) {
        validateRequired(price, "가격은 필수입니다.");
        validatePositiveQuantity(quantity);

        return normalizeCurrencyAmount(currencyCode, price.multiply(quantity), roundingMode);
    }

    private TradingAmountValidator() {
    }
}
