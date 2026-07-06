package com.finmate.domain.normal.account.dto;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import lombok.Getter;

import java.math.BigDecimal;

// 대표계좌 출력용
@Getter
public class PrimaryAccount {

    private final String accountNumber;
    private final BigDecimal balance;
    private final BankCode bankCode;

    public PrimaryAccount(Account account) {
        this.accountNumber = account.getAccountNumber();
        this.balance = account.getBalance();
        this.bankCode = account.getBankCode();
    }
}
