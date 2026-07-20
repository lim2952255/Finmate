package com.finmate.domain.investment.dto.exchange;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.cash.exchange.InvestmentCurrencyExchangeTransaction;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 환전 거래내역 페이지에 필요한 정보들을 담는 dto
@Getter
public class InvestmentCurrencyExchangeTransactionPageInfo {
    private final List<Investment> investments; // 사용자가 보유중인 증권계좌 목록
    private final Investment selectedInvestment; // 사용자가 선택한 증권계좌
    private final TransactionPeriod period; // 거래내역을 조회할 기간
    private final TransactionPeriod[] periods; // 사용자가 선택할 수 있는 기간 목록
    private final Page<InvestmentCurrencyExchangeTransaction> transactionPage; // 환전 거래내역 페이징 전체결과
    private final List<InvestmentCurrencyExchangeTransaction> transactions; // 환전 거래내역 목록 (transactionPage에서 현재 화면에 노출할 거래내역 일부)
    private final PaginationInfo pagination; // Paging 정보를 담은 객체

    public InvestmentCurrencyExchangeTransactionPageInfo(
            List<Investment> investments,
            Investment selectedInvestment,
            TransactionPeriod period,
            TransactionPeriod[] periods,
            Page<InvestmentCurrencyExchangeTransaction> transactionPage) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.period = period;
        this.periods = periods;
        this.transactionPage = transactionPage;
        this.transactions = transactionPage.getContent();
        this.pagination = PaginationInfo.from(transactionPage);
    }
}
