package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.detail.DomesticStockDailyFlowSnapshot;
import com.finmate.infra.kis.parser.KisValueParser;
import com.finmate.infra.kis.stock.detail.KisDailyLoanTransactionResponse;
import com.finmate.infra.kis.stock.detail.KisDailyShortSaleResponse;
import com.finmate.infra.kis.stock.detail.KisDomesticStockDetailClient;
import com.finmate.infra.kis.stock.detail.KisInvestorTradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 금일 장중 데이터를 KIS에서 읽어 Redis용 스냅샷으로 변환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockIntradayDailyFlowService {
    private final KisDomesticStockDetailClient kisClient;

    public DomesticStockDailyFlowSnapshot fetch(Stock stock, LocalDate tradeDate) {
        return new DomesticStockDailyFlowSnapshot(
                tradeDate,
                fetchInvestor(stock, tradeDate),
                fetchShortSale(stock, tradeDate),
                fetchLoan(stock, tradeDate),
                LocalDateTime.now());
    }

    private DomesticStockDailyFlowSnapshot.InvestorTrade fetchInvestor(Stock stock, LocalDate tradeDate) {
        try {
            KisInvestorTradeResponse response = kisClient.fetchDailyInvestorTrades(stock.getSymbol(), tradeDate);
            return safeList(response == null ? null : response.output2()).stream()
                    .filter(item -> tradeDate.equals(date(item.tradeDate())))
                    .findFirst()
                    .map(item -> new DomesticStockDailyFlowSnapshot.InvestorTrade(
                            decimal(item.closePrice()),
                            longValue(item.foreignBuyQuantity()),
                            longValue(item.foreignSellQuantity()),
                            longValue(item.foreignNetBuyQuantity()),
                            longValue(item.personalBuyQuantity()),
                            longValue(item.personalSellQuantity()),
                            longValue(item.personalNetBuyQuantity()),
                            longValue(item.institutionBuyQuantity()),
                            longValue(item.institutionSellQuantity()),
                            longValue(item.institutionNetBuyQuantity())))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("국내 종목 금일 투자자 수급 조회 실패. symbol={}", stock.getSymbol(), e);
            return null;
        }
    }

    private DomesticStockDailyFlowSnapshot.ShortSale fetchShortSale(Stock stock, LocalDate tradeDate) {
        try {
            KisDailyShortSaleResponse response = kisClient.fetchDailyShortSales(
                    stock.getSymbol(), tradeDate, tradeDate);
            return safeList(response == null ? null : response.output2()).stream()
                    .filter(item -> tradeDate.equals(date(item.tradeDate())))
                    .findFirst()
                    .map(item -> new DomesticStockDailyFlowSnapshot.ShortSale(
                            decimal(item.closePrice()),
                            longValue(item.accumulatedVolume()),
                            longValue(item.shortSaleQuantity()),
                            decimal(item.shortSaleVolumeRatio()),
                            decimal(item.accumulatedTradeAmount()),
                            decimal(item.shortSaleTradeAmount()),
                            decimal(item.shortSaleTradeAmountRatio()),
                            decimal(item.averagePrice())))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("국내 종목 금일 공매도 조회 실패. symbol={}", stock.getSymbol(), e);
            return null;
        }
    }

    private DomesticStockDailyFlowSnapshot.LoanTransaction fetchLoan(Stock stock, LocalDate tradeDate) {
        try {
            KisDailyLoanTransactionResponse response = kisClient.fetchDailyLoanTransactions(
                    stock.getSymbol(), tradeDate, tradeDate);
            return (response == null ? List.<Map<String, String>>of() : response.rows()).stream()
                    .filter(item -> tradeDate.equals(date(firstValue(
                            item, "stck_bsop_date", "bsop_date", "deal_date"))))
                    .findFirst()
                    .map(item -> new DomesticStockDailyFlowSnapshot.LoanTransaction(
                            decimal(firstValue(item, "stck_prpr", "stck_clpr", "close_price")),
                            longValue(firstValue(item, "new_stcn", "new_stln_qty", "stln_new_qty")),
                            longValue(firstValue(item, "rdmp_stcn", "rdmp_stln_qty",
                                    "stln_rdmn_qty", "stln_repay_qty")),
                            longValue(firstValue(item, "rmnd_stcn", "stln_blce_qty",
                                    "loan_balance_qty")),
                            decimal(firstValue(item, "new_stln_amt", "stln_new_amt")),
                            decimal(firstValue(item, "rdmp_stln_amt", "stln_rdmn_amt",
                                    "stln_repay_amt")),
                            decimal(firstValue(item, "rmnd_amt", "stln_blce_amt",
                                    "loan_balance_amt"))))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("국내 종목 금일 대차 조회 실패. symbol={}", stock.getSymbol(), e);
            return null;
        }
    }

    private LocalDate date(String value) {
        try {
            return KisValueParser.parseNullableDate(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(String value) {
        return KisValueParser.parseNullableBigDecimalOrNull(value);
    }

    private Long longValue(String value) {
        return KisValueParser.parseNullableLongOrNull(value);
    }

    private String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
