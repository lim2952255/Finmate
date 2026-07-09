package com.finmate.domain.normal.account.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import lombok.Getter;

import java.math.BigDecimal;

// 대표계좌 출력용
@Getter
public class PrimaryAccount {

    private final String accountNumber;
    private final BigDecimal balance;
    private final CurrencyCode currencyCode; // 대표계좌에도 통화 정보를 추가
    private final BankCode bankCode;

    public PrimaryAccount(Account account) {
        this.accountNumber = account.getAccountNumber();
        this.balance = account.getBalance();
        this.currencyCode = account.getCurrencyCode();
        this.bankCode = account.getBankCode();
    }
}
