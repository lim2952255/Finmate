package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// React 투자 홈과 증권계좌 목록 화면이 함께 사용하는 JSON 응답 구조다.
public record InvestmentOverviewResponse(
        List<CurrencyBalanceResponse> totalBalances,
        int investmentAccountCount,
        InvestmentResponse primaryInvestment,
        List<InvestmentResponse> investments
) {

    public static InvestmentOverviewResponse from(InvestmentHomeInfo homeInfo) {
        List<CurrencyBalanceResponse> totalBalances = homeInfo.getTotalDepositBalancesByCurrency().entrySet().stream()
                .map(CurrencyBalanceResponse::from)
                .toList();
        List<InvestmentResponse> investments = homeInfo.getInvestments().stream()
                .map(InvestmentResponse::from)
                .toList();
        InvestmentResponse primaryInvestment = investments.stream()
                .filter(InvestmentResponse::primary)
                .findFirst()
                .orElse(null);

        return new InvestmentOverviewResponse(
                totalBalances,
                homeInfo.getInvestmentAccountCount(),
                primaryInvestment,
                investments);
    }

    // 금액은 JavaScript Number의 정밀도 손실을 피하기 위해 문자열로 전달한다.
    public record CurrencyBalanceResponse(
            String currencyCode,
            int fractionDigits,
            String amount
    ) {
        private static CurrencyBalanceResponse from(Map.Entry<CurrencyCode, BigDecimal> entry) {
            CurrencyCode currencyCode = entry.getKey();
            return new CurrencyBalanceResponse(
                    currencyCode.name(),
                    currencyCode.getFractionDigits(),
                    entry.getValue().toPlainString());
        }
    }

    public record InvestmentResponse(
            Long id,
            String accountNumber,
            String securitiesCompanyCode,
            String securitiesCompanyName,
            boolean primary,
            List<CashBalanceResponse> cashBalances
    ) {
        private static InvestmentResponse from(Investment investment) {
            return new InvestmentResponse(
                    investment.getId(),
                    investment.getAccountNumber(),
                    investment.getSecuritiesCompanyCode().name(),
                    investment.getSecuritiesCompanyCode().getDisplayName(),
                    investment.isPrimary(),
                    investment.getCashBalances().stream()
                            .map(CashBalanceResponse::from)
                            .toList());
        }
    }

    public record CashBalanceResponse(
            String currencyCode,
            int fractionDigits,
            String availableBalance
    ) {
        private static CashBalanceResponse from(InvestmentCashBalance cashBalance) {
            return new CashBalanceResponse(
                    cashBalance.getCurrencyCode().name(),
                    cashBalance.getCurrencyCode().getFractionDigits(),
                    cashBalance.getAvailableBalance().toPlainString());
        }
    }
}
