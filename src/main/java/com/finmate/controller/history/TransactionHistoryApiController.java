package com.finmate.controller.history;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.cash.exchange.InvestmentCurrencyExchangeTransaction;
import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransaction;
import com.finmate.domain.investment.dto.cash.SecuritiesCashTransactionPageInfo;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeTransactionPageInfo;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.transaction.AccountTransaction;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.normal.account.transaction.dto.AccountTransactionPageInfo;
import com.finmate.domain.normal.account.transaction.dto.TransactionSummary;
import com.finmate.domain.normal.account.transaction.dto.TransactionSummaryByCurrency;
import com.finmate.global.pagination.PaginationInfo;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.investment.InvestmentCurrencyExchangeService;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.normal.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/history")
public class TransactionHistoryApiController {
    private final AccountService accountService;
    private final InvestmentService investmentService;
    private final InvestmentCurrencyExchangeService exchangeService;

    @GetMapping("/accounts")
    public HistoryResponse accounts(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) BankCode bankCode,
            @RequestParam(defaultValue = "THREE_MONTHS") TransactionPeriod period,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        AccountTransactionPageInfo info = accountService.getAccountTransactionPageInfo(
                principal.getId(), accountNumber, bankCode, period, page);
        return new HistoryResponse(
                info.getAccounts().stream().map(AccountChoice::from).toList(),
                info.getSelectedAccount() == null ? null : info.getSelectedAccount().getAccountNumber(),
                period.name(), periods(),
                info.getTransactionSummariesByCurrency().stream().map(HistorySummary::from).toList(),
                info.getTransactions().stream().map(HistoryRow::from).toList(),
                PageInfo.from(info.getTransactionPage().getNumber(), info.getTransactionPage().getTotalPages(), info.getPagination()));
    }

    @GetMapping("/cash")
    public HistoryResponse cash(
            @RequestParam(required = false) String investmentNumber,
            @RequestParam(required = false) SecuritiesCompanyCode securitiesCompanyCode,
            @RequestParam(defaultValue = "THREE_MONTHS") TransactionPeriod period,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        SecuritiesCashTransactionPageInfo info = investmentService.getSecuritiesCashTransactionPageInfo(
                principal.getId(), investmentNumber, securitiesCompanyCode, period, page);
        return new HistoryResponse(
                info.getInvestments().stream().map(AccountChoice::from).toList(),
                info.getSelectedInvestment() == null ? null : info.getSelectedInvestment().getAccountNumber(),
                period.name(), periods(),
                List.of(HistorySummary.from(info.getTransactionSummary(), null)),
                info.getTransactions().stream().map(HistoryRow::from).toList(),
                PageInfo.from(info.getTransactionPage().getNumber(), info.getTransactionPage().getTotalPages(), info.getPagination()));
    }

    @GetMapping("/exchanges")
    public HistoryResponse exchanges(
            @RequestParam(required = false) Long investmentId,
            @RequestParam(defaultValue = "THREE_MONTHS") TransactionPeriod period,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        InvestmentCurrencyExchangeTransactionPageInfo info = exchangeService.getCurrencyExchangeTransactionPageInfo(
                principal.getId(), investmentId, period, page);
        return new HistoryResponse(
                info.getInvestments().stream().map(AccountChoice::from).toList(),
                info.getSelectedInvestment() == null ? null : String.valueOf(info.getSelectedInvestment().getId()),
                period.name(), periods(),
                List.of(),
                info.getTransactions().stream().map(HistoryRow::from).toList(),
                PageInfo.from(info.getTransactionPage().getNumber(), info.getTransactionPage().getTotalPages(), info.getPagination()));
    }

    private List<Option> periods() {
        return Arrays.stream(TransactionPeriod.values()).map(value -> new Option(value.name(), value.getDisplayName())).toList();
    }

    public record HistoryResponse(List<AccountChoice> accounts, String selected, String period, List<Option> periods,
                                  List<HistorySummary> summaries, List<HistoryRow> rows, PageInfo page) {
    }
    public record Option(String value, String label) {
    }
    public record AccountChoice(Long id, String accountNumber, String companyCode, String companyName,
                                String currency, String balance, List<CashBalanceChoice> cashBalances) {
        static AccountChoice from(Account account) {
            return new AccountChoice(account.getId(), account.getAccountNumber(), account.getBankCode().name(),
                    account.getBankCode().getDisplayName(), account.getCurrencyCode().name(),
                    account.getBalance().toPlainString(), List.of());
        }

        static AccountChoice from(Investment investment) {
            return new AccountChoice(investment.getId(), investment.getAccountNumber(),
                    investment.getSecuritiesCompanyCode().name(), investment.getSecuritiesCompanyCode().getDisplayName(),
                    null, null, investment.getCashBalances().stream().map(CashBalanceChoice::from).toList());
        }
    }
    public record CashBalanceChoice(String currency, String availableBalance, String lockedBalance, String totalBalance) {
        static CashBalanceChoice from(com.finmate.domain.investment.InvestmentCashBalance balance) {
            return new CashBalanceChoice(balance.getCurrencyCode().name(), balance.getAvailableBalance().toPlainString(),
                    balance.getLockedBalance().toPlainString(), balance.getTotalBalance().toPlainString());
        }
    }
    public record HistorySummary(String currency, String totalDepositAmount, String totalWithdrawalAmount, String netAmount) {
        static HistorySummary from(TransactionSummaryByCurrency value) {
            return new HistorySummary(value.getCurrencyCode().name(), value.getTotalDepositAmount().toPlainString(),
                    value.getTotalWithdrawalAmount().toPlainString(), value.getNetAmount().toPlainString());
        }

        static HistorySummary from(TransactionSummary value, String currency) {
            return new HistorySummary(currency, value.getTotalDepositAmount().toPlainString(),
                    value.getTotalWithdrawalAmount().toPlainString(), value.getNetAmount().toPlainString());
        }
    }
    public record HistoryRow(String date, String accountName, String accountNumber, String type, String amount,
                             String currency, String beforeAmount, String afterAmount, String detail,
                             String counterpartyCompany, String counterpartyAccountNumber, String counterpartyName,
                             String toAmount, String toCurrency, String exchangeRate,
                             String toBalanceBefore, String toBalanceAfter) {
        static HistoryRow from(AccountTransaction value) {
            String counterpartyCompany = value.getCounterpartyBankCode() != null
                    ? value.getCounterpartyBankCode().getDisplayName()
                    : value.getCounterpartySecuritiesCompanyCode() != null
                    ? value.getCounterpartySecuritiesCompanyCode().getDisplayName()
                    : null;
            return new HistoryRow(value.getCreatedAt().toString(), value.getAccount().getBankCode().getDisplayName(),
                    value.getAccount().getAccountNumber(), value.getType().name(), value.getAmount().toPlainString(),
                    value.getAccount().getCurrencyCode().name(), value.getBalanceBeforeTransaction().toPlainString(),
                    value.getBalanceAfterTransaction().toPlainString(), value.getDescription(), counterpartyCompany,
                    value.getCounterpartyAccountNumber(), value.getCounterpartyName(), null, null, null, null, null);
        }

        static HistoryRow from(SecuritiesCashTransaction value) {
            return new HistoryRow(value.getCreatedAt().toString(), value.getInvestment().getSecuritiesCompanyCode().getDisplayName(),
                    value.getInvestment().getAccountNumber(), value.getType().name(), value.getAmount().toPlainString(),
                    value.getTransfer().getCurrencyCode().name(), value.getBalanceBeforeTransaction().toPlainString(),
                    value.getBalanceAfterTransaction().toPlainString(), value.getDescription(),
                    value.getCounterpartyBankCode() == null ? null : value.getCounterpartyBankCode().getDisplayName(),
                    value.getCounterpartyAccountNumber(), value.getCounterpartyName(), null, null, null, null, null);
        }

        static HistoryRow from(InvestmentCurrencyExchangeTransaction value) {
            return new HistoryRow(value.getCreatedAt().toString(), value.getInvestment().getSecuritiesCompanyCode().getDisplayName(),
                    value.getInvestment().getAccountNumber(), "EXCHANGE", value.getFromAmount().toPlainString(),
                    value.getFromCurrencyCode().name(), value.getFromBalanceBeforeExchange().toPlainString(),
                    value.getFromBalanceAfterExchange().toPlainString(), null, null, null, null,
                    value.getToAmount().toPlainString(), value.getToCurrencyCode().name(), value.getExchangeRate().toPlainString(),
                    value.getToBalanceBeforeExchange().toPlainString(), value.getToBalanceAfterExchange().toPlainString());
        }
    }
    public record PageInfo(int number, int totalPages, List<Integer> pageNumbers, boolean first, boolean last) {
        static PageInfo from(int number, int totalPages, PaginationInfo pagination) { return new PageInfo(number, totalPages, pagination.getPageNumbers(), number == 0, totalPages == 0 || number >= totalPages - 1); }
    }
}
