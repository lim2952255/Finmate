package com.finmate.domain.normal.account.transaction.dto;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.transaction.AccountTransaction;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 일반 계좌 거래내역 화면에 필요한 정보 DTO
@Getter
public class AccountTransactionPageInfo {
    private final List<Account> accounts;
    private final Account selectedAccount;
    private final TransactionPeriod period;
    private final TransactionPeriod[] periods;
    private final Page<AccountTransaction> transactionPage;
    private final List<AccountTransaction> transactions;
    private final TransactionSummary transactionSummary;
    private final List<TransactionSummaryByCurrency> transactionSummariesByCurrency;
    private final PaginationInfo pagination;

    public AccountTransactionPageInfo(List<Account> accounts,
                                      Account selectedAccount,
                                      TransactionPeriod period,
                                      TransactionPeriod[] periods,
                                      Page<AccountTransaction> transactionPage,
                                      TransactionSummary transactionSummary,
                                      List<TransactionSummaryByCurrency> transactionSummariesByCurrency) {
        this.accounts = accounts;
        this.selectedAccount = selectedAccount;
        this.period = period;
        this.periods = periods;
        this.transactionPage = transactionPage;
        this.transactions = transactionPage.getContent();
        this.transactionSummary = transactionSummary;
        this.transactionSummariesByCurrency = transactionSummariesByCurrency;
        this.pagination = PaginationInfo.from(transactionPage);
    }
}
