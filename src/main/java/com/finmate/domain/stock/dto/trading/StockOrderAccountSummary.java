package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.trading.StockHolding;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class StockOrderAccountSummary {
    private final Long investmentId;
    private final BigDecimal availableCashBalance;
    private final BigDecimal lockedCashBalance;
    private final BigDecimal totalCashBalance;
    private final BigDecimal holdingQuantity;
    private final BigDecimal lockedQuantity;
    private final BigDecimal availableQuantity;

    private StockOrderAccountSummary(Long investmentId,
                                     BigDecimal availableCashBalance,
                                     BigDecimal lockedCashBalance,
                                     BigDecimal totalCashBalance,
                                     BigDecimal holdingQuantity,
                                     BigDecimal lockedQuantity,
                                     BigDecimal availableQuantity) {
        this.investmentId = investmentId;
        this.availableCashBalance = availableCashBalance;
        this.lockedCashBalance = lockedCashBalance;
        this.totalCashBalance = totalCashBalance;
        this.holdingQuantity = holdingQuantity;
        this.lockedQuantity = lockedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public static StockOrderAccountSummary from(Investment investment,
                                                CurrencyCode currencyCode,
                                                StockHolding holding) {
        InvestmentCashBalance cashBalance = investment.getCashBalances().stream()
                .filter(balance -> balance.getCurrencyCode() == currencyCode)
                .findFirst()
                .orElse(null);

        BigDecimal availableCashBalance = cashBalance == null ? BigDecimal.ZERO : cashBalance.getAvailableBalance();
        BigDecimal lockedCashBalance = cashBalance == null ? BigDecimal.ZERO : cashBalance.getLockedBalance();
        BigDecimal totalCashBalance = cashBalance == null ? BigDecimal.ZERO : cashBalance.getTotalBalance();
        BigDecimal holdingQuantity = holding == null ? BigDecimal.ZERO : holding.getQuantity();
        BigDecimal lockedQuantity = holding == null ? BigDecimal.ZERO : holding.getLockedQuantity();
        BigDecimal availableQuantity = holding == null ? BigDecimal.ZERO : holding.getAvailableQuantity();

        return new StockOrderAccountSummary(
                investment.getId(),
                availableCashBalance,
                lockedCashBalance,
                totalCashBalance,
                holdingQuantity,
                lockedQuantity,
                availableQuantity);
    }
}
