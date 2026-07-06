package com.finmate.domain.normal.account.dto;

import lombok.Getter;

import java.math.BigDecimal;

// 계좌 홈 화면 요약 정보 DTO
@Getter
public class AccountHomeInfo {
    private final PrimaryAccount primaryAccount;
    private final BigDecimal totalBalance;
    private final int accountCount;

    public AccountHomeInfo(PrimaryAccount primaryAccount,
                           BigDecimal totalBalance,
                           int accountCount) {
        this.primaryAccount = primaryAccount;
        this.totalBalance = totalBalance;
        this.accountCount = accountCount;
    }
}
