package com.finmate.domain.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 각 통화 종류, 소수점 자릿수, 금액 scale 검증
public enum CurrencyCode {
    KRW("원화", "₩", 0),
    JPY("엔화", "¥", 0),
    EUR("유로", "€", 2),
    USD("달러", "$", 2);

    public static final CurrencyCode DEFAULT = KRW;

    private final String displayName;
    private final String symbol;
    private final int fractionDigits;

    CurrencyCode(String displayName, String symbol, int fractionDigits) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.fractionDigits = fractionDigits;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getFractionDigits() {
        return fractionDigits;
    }

    public BigDecimal getMinimumAmount() {
        return BigDecimal.ONE.movePointLeft(fractionDigits);
    }

    public BigDecimal getInputStep() {
        return getMinimumAmount();
    }

    public void validateAmountScale(BigDecimal amount) {
        if (amount == null) {
            throw new RuntimeException("금액은 필수입니다.");
        }

        try {
            amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            if (fractionDigits == 0) {
                throw new RuntimeException(name() + "는 소수점 없이 입력해주세요.");
            }

            throw new RuntimeException(name() + "는 소수점 " + fractionDigits + "자리까지 입력할 수 있습니다.");
        }
    }
}
