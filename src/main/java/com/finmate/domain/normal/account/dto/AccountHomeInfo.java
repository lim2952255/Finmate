package com.finmate.domain.normal.account.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.Account;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// 계좌 홈 화면 요약 정보 DTO
@Getter
public class AccountHomeInfo {
    private final List<Account> accounts;
    private final PrimaryAccount primaryAccount;
    private final BigDecimal totalBalance;
    private final Map<CurrencyCode, BigDecimal> totalBalancesByCurrency;
    private final int accountCount;

    public AccountHomeInfo(List<Account> accounts,
                           PrimaryAccount primaryAccount,
                           Map<CurrencyCode, BigDecimal> totalBalancesByCurrency,
                           int accountCount) {
        this.accounts = accounts;
        this.primaryAccount = primaryAccount;
        this.totalBalancesByCurrency = totalBalancesByCurrency; // 통화별 금액
        this.totalBalance = totalBalancesByCurrency.getOrDefault(CurrencyCode.KRW, BigDecimal.ZERO);
        this.accountCount = accountCount;
    }
}
