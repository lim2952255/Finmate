package com.finmate.domain.normal.account.dto;

import lombok.Getter;

import java.math.BigDecimal;

// 이체한도 화면 표시용 DTO
@Getter
public class TransferLimitInfo {
    private final BigDecimal dailyTransferLimit;
    private final BigDecimal singleTransferLimit;
    private final BigDecimal todayUsedTransferAmount;
    private final BigDecimal remainingDailyTransferLimit;

    public TransferLimitInfo(BigDecimal dailyTransferLimit,
                             BigDecimal singleTransferLimit,
                             BigDecimal todayUsedTransferAmount) {
        this.dailyTransferLimit = dailyTransferLimit;
        this.singleTransferLimit = singleTransferLimit;
        this.todayUsedTransferAmount = todayUsedTransferAmount;
        this.remainingDailyTransferLimit = calculateRemainingDailyTransferLimit(
                dailyTransferLimit,
                todayUsedTransferAmount);
    }

    public static TransferLimitInfo zero() {
        return new TransferLimitInfo(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private BigDecimal calculateRemainingDailyTransferLimit(BigDecimal dailyTransferLimit,
                                                            BigDecimal todayUsedTransferAmount) {
        BigDecimal remainingDailyTransferLimit = dailyTransferLimit.subtract(todayUsedTransferAmount);
        if (remainingDailyTransferLimit.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remainingDailyTransferLimit;
    }
}
