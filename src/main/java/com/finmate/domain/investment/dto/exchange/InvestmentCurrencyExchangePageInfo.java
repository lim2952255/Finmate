package com.finmate.domain.investment.dto.exchange;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 증권계좌 환전 페이지에 필요한 정보를 담은 DTO
@Getter
public class InvestmentCurrencyExchangePageInfo {
    private final InvestmentCurrencyExchangeRequest investmentCurrencyExchangeRequest; // 사용자가 입력한 환전정보
    private final List<Investment> investments; // 사용자가 보유한 증권계좌 목록
    private final Investment selectedInvestment; // 사용자가 선택한 증권계좌
    private final CurrencyCode[] currencyCodes; // 애플리케이션에서 지원하는 통화 종류
    private final BigDecimal usdKrwExchangeRate; // 기준 환율

    public InvestmentCurrencyExchangePageInfo(InvestmentCurrencyExchangeRequest investmentCurrencyExchangeRequest,
                                              List<Investment> investments,
                                              Investment selectedInvestment,
                                              BigDecimal usdKrwExchangeRate) {
        this.investmentCurrencyExchangeRequest = investmentCurrencyExchangeRequest;
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.currencyCodes = CurrencyCode.values();
        this.usdKrwExchangeRate = usdKrwExchangeRate;
    }
}
