package com.finmate.domain.normal.account;

import com.finmate.exception.BusinessRuleException;
import com.finmate.domain.investment.CurrencyCode;

import java.math.BigDecimal;

public final class AccountBalancePolicy {
    // 지급 대상 사용자가 받을 모의자금 액수만 반환하며, 실제 입금과 지급 이력 갱신은 개설 서비스가 담당한다.
    public static BigDecimal initialBalanceOf(CurrencyCode currencyCode) {
        if (currencyCode == null) {
            throw new BusinessRuleException("통화는 필수입니다.");
        }

        // 지급 자격은 개설 서비스에서 사용자 단위로 확인한다. 외화 계좌에는 지급하지 않는다.
        return switch (currencyCode) {
            case KRW -> BigDecimal.valueOf(100_000_000);
            case USD -> BigDecimal.ZERO;
        };
    }

    private AccountBalancePolicy() {
    }
}
