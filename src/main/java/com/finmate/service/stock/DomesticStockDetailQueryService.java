package com.finmate.service.stock;

import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import com.finmate.domain.stock.metadata.domestic.DomesticFinancialPeriodType;
import com.finmate.repository.stock.metadata.domestic.DomesticStockBalanceSheetRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockCurrentQuoteRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockDetailRefreshStateRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockFinancialRatioRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockIncomeStatementRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockInvestorDailyTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 현재가·재무·투자자 Repository 조회 결과를 하나의 화면 DTO로 조합한다.
@Service
@RequiredArgsConstructor
public class DomesticStockDetailQueryService {
    private static final String KRX_MARKET_CODE = "J";
    private static final DomesticFinancialPeriodType PERIOD_TYPE = DomesticFinancialPeriodType.QUARTERLY;

    private final DomesticStockCurrentQuoteRepository currentQuoteRepository;
    private final DomesticStockFinancialRatioRepository financialRatioRepository;
    private final DomesticStockIncomeStatementRepository incomeStatementRepository;
    private final DomesticStockBalanceSheetRepository balanceSheetRepository;
    private final DomesticStockInvestorDailyTradeRepository investorDailyTradeRepository;
    private final DomesticStockDetailRefreshStateRepository refreshStateRepository;

    public DomesticStockDetailInfo getDetailInfo(Long stockId, DomesticStockCurrentQuoteSnapshot currentQuote) {
        return DomesticStockDetailInfo.of(
                currentQuoteRepository.findByStock_Id(stockId).orElse(null),
                currentQuote,
                financialRatioRepository.findTop4ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
                        stockId, PERIOD_TYPE),
                incomeStatementRepository.findTop6ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
                        stockId, PERIOD_TYPE),
                balanceSheetRepository.findTop4ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
                        stockId, PERIOD_TYPE),
                investorDailyTradeRepository.findTop20ByStock_IdAndMarketCodeOrderByTradeDateDesc(
                        stockId, KRX_MARKET_CODE),
                refreshStateRepository.findByStock_Id(stockId).orElse(null));
    }
}
