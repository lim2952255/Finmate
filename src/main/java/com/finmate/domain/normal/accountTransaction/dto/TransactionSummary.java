package com.finmate.domain.normal.accountTransaction.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TransactionSummary {
    private final BigDecimal totalDepositAmount;
    private final BigDecimal totalWithdrawalAmount;
    private final BigDecimal netAmount;

    // 총 입금액과 총 출금액을 계산
    public TransactionSummary(BigDecimal totalDepositAmount, BigDecimal totalWithdrawalAmount) {
        this.totalDepositAmount = totalDepositAmount == null ? BigDecimal.ZERO : totalDepositAmount;
        this.totalWithdrawalAmount = totalWithdrawalAmount == null ? BigDecimal.ZERO : totalWithdrawalAmount;
        this.netAmount = this.totalDepositAmount.subtract(this.totalWithdrawalAmount);
    }
}
