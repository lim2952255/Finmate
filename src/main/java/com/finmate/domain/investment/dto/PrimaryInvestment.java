package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class PrimaryInvestment {

    private final String accountNumber;
    private final BigDecimal depositBalance;
    private final SecuritiesCompanyCode securitiesCompanyCode;

    public PrimaryInvestment(Investment investment) {
        this.accountNumber = investment.getAccountNumber();
        this.depositBalance = investment.getDepositBalance();
        this.securitiesCompanyCode = investment.getSecuritiesCompanyCode();
    }
}
