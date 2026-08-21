package com.finmate.domain.normal.account.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// React 계좌 홈과 계좌 목록 화면이 함께 사용하는 JSON 응답 구조다.
public record AccountOverviewResponse(
        List<CurrencyBalanceResponse> totalBalances,
        int accountCount,
        AccountResponse primaryAccount,
        List<AccountResponse> accounts
) {

    public static AccountOverviewResponse from(AccountHomeInfo homeInfo) {
        List<CurrencyBalanceResponse> totalBalances = homeInfo.getTotalBalancesByCurrency().entrySet().stream()
                .map(CurrencyBalanceResponse::from)
                .toList();
        List<AccountResponse> accountResponses = homeInfo.getAccounts().stream()
                .map(AccountResponse::from)
                .toList();
        AccountResponse primaryAccount = accountResponses.stream()
                .filter(AccountResponse::primary)
                .findFirst()
                .orElse(null);

        return new AccountOverviewResponse(
                totalBalances,
                homeInfo.getAccountCount(),
                primaryAccount,
                accountResponses);
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

    public record AccountResponse(
            Long id,
            String accountNumber,
            String bankCode,
            String bankName,
            String currencyCode,
            int fractionDigits,
            String balance,
            boolean primary
    ) {
        private static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getAccountNumber(),
                    account.getBankCode().name(),
                    account.getBankCode().getDisplayName(),
                    account.getCurrencyCode().name(),
                    account.getCurrencyCode().getFractionDigits(),
                    account.getBalance().toPlainString(),
                    account.isPrimary());
        }
    }
}
