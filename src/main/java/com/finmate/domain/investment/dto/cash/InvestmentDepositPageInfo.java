package com.finmate.domain.investment.dto.cash;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.normal.account.Account;
import lombok.Getter;

import java.util.List;

// 증권계좌 예수금 입금 화면에 필요한 정보 DTO
@Getter
public class InvestmentDepositPageInfo {
    private final InvestmentDepositRequest investmentDepositRequest;
    private final List<Account> accounts;
    private final List<Investment> investments;
    private final Account fromAccount;

    public InvestmentDepositPageInfo(InvestmentDepositRequest investmentDepositRequest,
                                     List<Account> accounts,
                                     List<Investment> investments,
                                     Account fromAccount) {
        this.investmentDepositRequest = investmentDepositRequest;
        this.accounts = accounts;
        this.investments = investments;
        this.fromAccount = fromAccount;
    }
}
