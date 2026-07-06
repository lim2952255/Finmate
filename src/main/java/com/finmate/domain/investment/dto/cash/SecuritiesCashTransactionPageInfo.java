package com.finmate.domain.investment.dto.cash;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransaction;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.normal.account.transaction.dto.TransactionSummary;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 예수금 입출금 내역 화면에 필요한 정보 DTO
@Getter
public class SecuritiesCashTransactionPageInfo {
    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final TransactionPeriod period;
    private final TransactionPeriod[] periods;
    private final Page<SecuritiesCashTransaction> transactionPage;
    private final List<SecuritiesCashTransaction> transactions;
    private final TransactionSummary transactionSummary;
    private final PaginationInfo pagination;

    public SecuritiesCashTransactionPageInfo(List<Investment> investments,
                                             Investment selectedInvestment,
                                             TransactionPeriod period,
                                             TransactionPeriod[] periods,
                                             Page<SecuritiesCashTransaction> transactionPage,
                                             TransactionSummary transactionSummary) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.period = period;
        this.periods = periods;
        this.transactionPage = transactionPage;
        this.transactions = transactionPage.getContent();
        this.transactionSummary = transactionSummary;
        this.pagination = PaginationInfo.from(transactionPage);
    }
}
