package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.Investment;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 투자 홈 화면 요약 정보 DTO
@Getter
public class InvestmentHomeInfo {
    private final List<Investment> investments;
    private final PrimaryInvestment primaryInvestment;
    private final BigDecimal totalDepositBalance;
    private final int investmentAccountCount;

    public InvestmentHomeInfo(List<Investment> investments,
                              PrimaryInvestment primaryInvestment,
                              BigDecimal totalDepositBalance,
                              int investmentAccountCount) {
        this.investments = investments;
        this.primaryInvestment = primaryInvestment;
        this.totalDepositBalance = totalDepositBalance;
        this.investmentAccountCount = investmentAccountCount;
    }
}
