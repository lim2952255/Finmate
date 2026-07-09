package com.finmate.domain.normal.account;

import com.finmate.domain.investment.CurrencyCode;

import java.math.BigDecimal;

public final class AccountBalancePolicy {
    public static BigDecimal initialBalanceOf(CurrencyCode currencyCode) {
        if (currencyCode == null) {
            throw new RuntimeException("통화는 필수입니다.");
        }

        // 원화가 아니라 다른 통화를 사용하는 경우에는 원화 계좌를 개설한 후 환전을 이용한다.
        return switch (currencyCode) {
            case KRW -> BigDecimal.valueOf(3_000_000);
            case USD -> BigDecimal.ZERO;
            case JPY -> BigDecimal.ZERO;
            case EUR -> BigDecimal.ZERO;
        };
    }

    private AccountBalancePolicy() {
    }
}
