package com.finmate.domain.normal.account.transaction.dto;

import com.finmate.domain.investment.CurrencyCode;
import lombok.Getter;

import java.math.BigDecimal;

// 통화별 총 입금액과 총 출금액을 출력하기 위한 DTO
@Getter
public class TransactionSummaryByCurrency {
    private final CurrencyCode currencyCode;
    private final BigDecimal totalDepositAmount;
    private final BigDecimal totalWithdrawalAmount;
    private final BigDecimal netAmount;

    public TransactionSummaryByCurrency(CurrencyCode currencyCode,
                                        BigDecimal totalDepositAmount,
                                        BigDecimal totalWithdrawalAmount) {
        this.currencyCode = currencyCode;
        this.totalDepositAmount = totalDepositAmount == null ? BigDecimal.ZERO : totalDepositAmount;
        this.totalWithdrawalAmount = totalWithdrawalAmount == null ? BigDecimal.ZERO : totalWithdrawalAmount;
        this.netAmount = this.totalDepositAmount.subtract(this.totalWithdrawalAmount);
    }
}
