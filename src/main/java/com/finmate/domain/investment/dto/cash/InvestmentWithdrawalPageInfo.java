package com.finmate.domain.investment.dto.cash;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.normal.account.Account;
import lombok.Getter;

import java.util.List;

// 증권 예수금 출금 화면에 필요한 정보 DTO
@Getter
public class InvestmentWithdrawalPageInfo {
    private final InvestmentWithdrawalRequest investmentWithdrawalRequest;
    private final List<Investment> investments;
    private final List<Account> accounts;
    private final Investment fromInvestment;

    public InvestmentWithdrawalPageInfo(InvestmentWithdrawalRequest investmentWithdrawalRequest,
                                        List<Investment> investments,
                                        List<Account> accounts,
                                        Investment fromInvestment) {
        this.investmentWithdrawalRequest = investmentWithdrawalRequest;
        this.investments = investments;
        this.accounts = accounts;
        this.fromInvestment = fromInvestment;
    }
}
