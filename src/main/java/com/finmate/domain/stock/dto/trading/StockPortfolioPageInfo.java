package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.dto.industry.StockIndustryClassification;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.unknownIfBlank;

@Getter
public class StockPortfolioPageInfo {
    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);

    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final List<StockHolding> holdings;
    private final boolean allAccounts;
    private final List<CurrencyCode> currencies;
    private final Map<CurrencyCode, BigDecimal> totalPurchaseAmountsByCurrency;
    private final Map<Long, StockIndustryClassification> industryClassificationsByStockId;
    private final Map<Long, StockPortfolioPriceSnapshot> priceSnapshotsByStockId;
    private final List<StockPortfolioIndustryAllocation> industryAllocations;
    private final Map<StockMarketType, Boolean> stockTradingAvailableByMarketType;
    private final Map<StockMarketType, String> stockTradingTimeDescriptionsByMarketType;

    public StockPortfolioPageInfo(List<Investment> investments,
                                  Investment selectedInvestment,
                                  List<StockHolding> holdings,
                                  boolean allAccounts) {
        this(investments, selectedInvestment, holdings, allAccounts, Map.of());
    }

    public StockPortfolioPageInfo(List<Investment> investments,
                                  Investment selectedInvestment,
                                  List<StockHolding> holdings,
                                  boolean allAccounts,
                                  Map<Long, StockIndustryClassification> industryClassificationsByStockId) {
        this(investments, selectedInvestment, holdings, allAccounts, industryClassificationsByStockId, Map.of());
    }

    public StockPortfolioPageInfo(List<Investment> investments,
                                  Investment selectedInvestment,
                                  List<StockHolding> holdings,
                                  boolean allAccounts,
                                  Map<Long, StockIndustryClassification> industryClassificationsByStockId,
                                  Map<Long, StockPortfolioPriceSnapshot> priceSnapshotsByStockId) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.holdings = holdings;
        this.allAccounts = allAccounts;
        this.industryClassificationsByStockId = industryClassificationsByStockId == null
                ? Map.of()
                : Map.copyOf(industryClassificationsByStockId);
        this.priceSnapshotsByStockId = priceSnapshotsByStockId == null ? Map.of() : Map.copyOf(priceSnapshotsByStockId);
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
        this.industryAllocations = calculateIndustryAllocations();
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

    public String formatPercent(BigDecimal value) {
        return DisplayFormatUtils.formatFixedDecimal(value, 2) + "%";
    }

    public BigDecimal getTotalPurchaseAmount(CurrencyCode currencyCode) {
        return totalPurchaseAmountsByCurrency.getOrDefault(currencyCode, BigDecimal.ZERO);
    }

    public String getIndustryName(Long stockId) {
        StockIndustryClassification classification = industryClassificationsByStockId.get(stockId);
        return classification == null ? unknownIfBlank(null) : classification.displayName();
    }

    public BigDecimal getValuationPrice(Long stockId) {
        StockPortfolioPriceSnapshot snapshot = priceSnapshotsByStockId.get(stockId);
        return snapshot == null ? null : snapshot.price();
    }

    public boolean hasValuationPrice(Long stockId) {
        return getValuationPrice(stockId) != null;
    }

    public String getValuationPriceSource(Long stockId) {
        StockPortfolioPriceSnapshot snapshot = priceSnapshotsByStockId.get(stockId);
        return snapshot == null ? "" : snapshot.sourceText();
    }

    public boolean isStockTradingAvailable(StockMarketType marketType) {
        return stockTradingAvailableByMarketType.getOrDefault(marketType, false);
    }

    public String getStockTradingTimeDescription(StockMarketType marketType) {
        return stockTradingTimeDescriptionsByMarketType.getOrDefault(marketType, "");
    }

    private List<StockPortfolioIndustryAllocation> calculateIndustryAllocations() {
        Map<CurrencyCode, Map<IndustryAllocationKey, BigDecimal>> purchaseAmountsByCurrencyAndIndustry = new LinkedHashMap<>();
        for (StockHolding holding : holdings) {
            CurrencyCode currencyCode = holding.getCurrencyCode();
            IndustryAllocationKey allocationKey = industryAllocationKey(holding);
            BigDecimal purchaseAmount = holding.getAveragePurchasePrice().multiply(holding.getQuantity());

            purchaseAmountsByCurrencyAndIndustry
                    .computeIfAbsent(currencyCode, ignored -> new LinkedHashMap<>())
                    .merge(allocationKey, purchaseAmount, BigDecimal::add);
        }

        List<StockPortfolioIndustryAllocation> allocations = new ArrayList<>();
        for (CurrencyCode currencyCode : currencies) {
            BigDecimal totalPurchaseAmount = getTotalPurchaseAmount(currencyCode);
            if (totalPurchaseAmount.signum() <= 0) {
                continue;
            }

            purchaseAmountsByCurrencyAndIndustry.getOrDefault(currencyCode, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<IndustryAllocationKey, BigDecimal>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(entry -> entry.getKey().groupName())
                            .thenComparing(entry -> entry.getKey().industryName()))
                    .map(entry -> new StockPortfolioIndustryAllocation(
                            currencyCode,
                            entry.getKey().groupName(),
                            entry.getKey().industryName(),
                            entry.getValue(),
                            entry.getValue()
                                    .multiply(PERCENT_MULTIPLIER)
                                    .divide(totalPurchaseAmount, 2, RoundingMode.HALF_UP)))
                    .forEach(allocations::add);
        }

        return List.copyOf(allocations);
    }

    private IndustryAllocationKey industryAllocationKey(StockHolding holding) {
        StockIndustryClassification classification = industryClassificationsByStockId
                .getOrDefault(holding.getStock().getId(), StockIndustryClassification.unclassified());
        return new IndustryAllocationKey(
                classification.allocationGroupNameOrDefault(),
                classification.allocationIndustryNameOrDefault());
    }

    private record IndustryAllocationKey(String groupName, String industryName) {
    }
}
