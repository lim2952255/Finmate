package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import lombok.Getter;

import java.math.BigDecimal;

// 증권계좌의 통화별 예수금을 출력하는 메서드
@Getter
public class InvestmentCashBalanceInfo {
    private final CurrencyCode currencyCode;
    private final BigDecimal availableBalance;
    private final BigDecimal lockedBalance;
    private final BigDecimal totalBalance;

    public InvestmentCashBalanceInfo(InvestmentCashBalance cashBalance) {
        this.currencyCode = cashBalance.getCurrencyCode();
        this.availableBalance = cashBalance.getAvailableBalance();
        this.lockedBalance = cashBalance.getLockedBalance();
        this.totalBalance = cashBalance.getTotalBalance();
    }
}
