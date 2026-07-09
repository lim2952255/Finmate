package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

public final class TradingAmountValidator {
    public static final int QUANTITY_SCALE = 6;

    public static void validatePositiveQuantity(BigDecimal quantity) {
        validateRequired(quantity, "수량은 필수입니다.");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("수량은 0보다 커야 합니다.");
        }
    }

    public static void validatePositivePrice(CurrencyCode currencyCode, BigDecimal price, String errorMessage) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        validateRequired(price, errorMessage);
        currencyCode.validateAmountScale(price);

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException(errorMessage);
        }
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

    public static void validateOrderExpiration(StockOrderType orderType, LocalDateTime expiresAt) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        if (orderType == StockOrderType.MARKET) {
            return;
        }

        validateRequired(expiresAt, "지정가 주문 만료시각은 필수입니다.");
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("지정가 주문 만료시각은 현재 시각 이후여야 합니다.");
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
