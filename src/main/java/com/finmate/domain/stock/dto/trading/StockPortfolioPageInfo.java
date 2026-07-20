package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class StockPortfolioPageInfo {
    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final List<StockHolding> holdings;
    private final boolean allAccounts;
    private final List<CurrencyCode> currencies;
    private final Map<CurrencyCode, BigDecimal> totalPurchaseAmountsByCurrency;
    private final Map<StockMarketType, Boolean> stockTradingAvailableByMarketType;
    private final Map<StockMarketType, String> stockTradingTimeDescriptionsByMarketType;

    public StockPortfolioPageInfo(List<Investment> investments,
                                  Investment selectedInvestment,
                                  List<StockHolding> holdings,
                                  boolean allAccounts) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.holdings = holdings;
        this.allAccounts = allAccounts;
        this.totalPurchaseAmountsByCurrency = holdings.stream()
                .collect(Collectors.groupingBy(
                        StockHolding::getCurrencyCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                holding -> holding.getAveragePurchasePrice().multiply(holding.getQuantity()),
                                BigDecimal::add
                        )
                ));
        this.currencies = Arrays.stream(CurrencyCode.values())
                .filter(totalPurchaseAmountsByCurrency::containsKey)
                .toList();
        // 포트폴리오 페이지의 주문 버튼도 종목 가능 시간대에만 활성화되도록 한다.
        // 현재 거래가 가능한 종목 목록
        this.stockTradingAvailableByMarketType = Arrays.stream(StockMarketType.values())
                .collect(Collectors.toMap(
                        marketType -> marketType,
                        StockMarketSchedules::isTradingTimeNow
                ));
        // 각 종목에 대한 거래 가능 시간 설명
        this.stockTradingTimeDescriptionsByMarketType = Arrays.stream(StockMarketType.values())
                .collect(Collectors.toMap(
                        marketType -> marketType,
                        StockMarketSchedules::tradingTimeDescription
                ));
    }

    public String formatQuantity(BigDecimal value) {
        return DisplayFormatUtils.formatDecimal(value, 6);
    }

    public String formatDecimal(BigDecimal value, int fractionDigits) {
        return DisplayFormatUtils.formatDecimal(value, fractionDigits);
    }

    public BigDecimal getTotalPurchaseAmount(CurrencyCode currencyCode) {
        return totalPurchaseAmountsByCurrency.getOrDefault(currencyCode, BigDecimal.ZERO);
    }

    public boolean isStockTradingAvailable(StockMarketType marketType) {
        return stockTradingAvailableByMarketType.getOrDefault(marketType, false);
    }

    public String getStockTradingTimeDescription(StockMarketType marketType) {
        return stockTradingTimeDescriptionsByMarketType.getOrDefault(marketType, "");
    }
}
