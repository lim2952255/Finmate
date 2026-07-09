package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 대표 증권 계좌 설정용 dto
@Getter
public class PrimaryInvestment {

    private final String accountNumber;
    private final BigDecimal depositBalance;
    private final List<InvestmentCashBalanceInfo> cashBalances;
    private final SecuritiesCompanyCode securitiesCompanyCode;

    public PrimaryInvestment(Investment investment) {
        this.accountNumber = investment.getAccountNumber();
        this.depositBalance = investment.getDepositBalance();
        // 각 CashBalance마다 InvestmentCashBalanceInfo를 생성
        this.cashBalances = investment.getCashBalances().stream()
                .map(InvestmentCashBalanceInfo::new)
                .toList();
        this.securitiesCompanyCode = investment.getSecuritiesCompanyCode();
    }
}
