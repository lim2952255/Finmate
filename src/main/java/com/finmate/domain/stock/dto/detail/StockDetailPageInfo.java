package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.Stock;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

// 종목 상세 페이지 전체에 필요한 데이터를 모아서 view에 넘기는 DTO
@Getter
public class StockDetailPageInfo {
    private final Stock stock;
    private final StockChartInterval selectedInterval;
    private final StockChartInterval[] chartIntervals;
    private final LocalDate chartStartDate;
    private final LocalDate chartEndDate;
    private final LocalDate latestTradeDate;
    private final BigDecimal latestClosePrice;
    private final BigDecimal latestChangeAmount;
    private final BigDecimal latestChangeRate;
    private final String latestPriceChangeClass;
    private final String currencySymbol;
    private final int priceDecimalDigits;
    private final int savedPriceCount;
    private final List<StockChartCandleData> chartCandles;
    private final LocalDate currentCandleTradeDate;
    private final Long currentCandleBaseVolume;
    private final BigDecimal currentCandleBaseTradeAmount;
    private final StockChartPriceSummary chartPriceSummary;
    private final StockMetadataDisplayInfo metadataDisplayInfo;
    private final DomesticStockDetailInfo domesticDetailInfo;
    private final boolean stockTradingAvailable;
    private final String stockTradingTimeDescription;

    public StockDetailPageInfo(Stock stock,
                               StockChartInterval selectedInterval,
                               LocalDate chartStartDate,
                               LocalDate chartEndDate,
                               int savedPriceCount,
                               List<StockChartCandleData> candles,
                               List<StockChartCandleData> latestDailyCandles,
                               LocalDate currentCandleTradeDate,
                               Long currentCandleBaseVolume,
                               BigDecimal currentCandleBaseTradeAmount,
                               StockMetadataDisplayInfo metadataDisplayInfo,
                               DomesticStockDetailInfo domesticDetailInfo,
                               boolean stockTradingAvailable,
                               String stockTradingTimeDescription) {
        this.stock = stock;
        this.selectedInterval = selectedInterval;
        this.chartIntervals = StockChartInterval.values();
        this.chartStartDate = chartStartDate;
        this.chartEndDate = chartEndDate;
        this.savedPriceCount = savedPriceCount;
        this.currentCandleTradeDate = currentCandleTradeDate;
        this.currentCandleBaseVolume = currentCandleBaseVolume;
        this.currentCandleBaseTradeAmount = currentCandleBaseTradeAmount;
        this.currencySymbol = resolveCurrencySymbol(stock);
        this.priceDecimalDigits = resolvePriceDecimalDigits(stock);

        List<StockChartCandleData> validCandles = candles.stream()
                .filter(this::hasValidPrice)
                .toList();
        List<StockChartCandleData> validDailyCandles = latestDailyCandles.stream()
                .filter(this::hasValidPrice)
                .toList();
        List<StockChartCandleData> quoteCandles = validDailyCandles.isEmpty()
                ? validCandles
                : validDailyCandles;
        this.chartCandles = validCandles;
        this.latestTradeDate = latestTradeDate(quoteCandles);
        this.latestClosePrice = latestClosePrice(quoteCandles);
        this.latestChangeAmount = latestChangeAmount(quoteCandles);
        this.latestChangeRate = latestChangeRate(this.latestChangeAmount, previousClosePrice(quoteCandles));
        this.latestPriceChangeClass = priceChangeClass(this.latestChangeAmount);
        this.chartPriceSummary = chartPriceSummary(validCandles);
        this.metadataDisplayInfo = metadataDisplayInfo;
        this.domesticDetailInfo = domesticDetailInfo;
        this.stockTradingAvailable = stockTradingAvailable;
        this.stockTradingTimeDescription = stockTradingTimeDescription;
    }

    public boolean hasPrices() {
        return !chartCandles.isEmpty();
    }

    public String formatPrice(BigDecimal value) {
        String formattedValue = DisplayFormatUtils.formatDecimal(value, priceDecimalDigits);
        return value == null ? formattedValue : currencySymbol + formattedValue;
    }

    public String formatLatestChangeAmount() {
        if (latestChangeAmount == null) {
            return DisplayFormatUtils.formatSignedDecimal(null, priceDecimalDigits);
        }

        String sign = latestChangeAmount.signum() > 0 ? "+" : latestChangeAmount.signum() < 0 ? "-" : "";
        return sign + currencySymbol
                + DisplayFormatUtils.formatDecimal(latestChangeAmount.abs(), priceDecimalDigits);
    }

    public String formatLatestChangeRate() {
        return DisplayFormatUtils.formatSignedPercent(latestChangeRate, 2);
    }

    private LocalDate latestTradeDate(List<StockChartCandleData> candles) {
        if (candles.isEmpty()) {
            return null;
        }
        return LocalDate.parse(candles.get(candles.size() - 1).tradeDate());
    }

    private BigDecimal latestClosePrice(List<StockChartCandleData> candles) {
        if (candles.isEmpty()) {
            return null;
        }
        return candles.get(candles.size() - 1).closePrice();
    }

    private BigDecimal previousClosePrice(List<StockChartCandleData> candles) {
        if (candles.size() < 2) {
            return null;
        }
        return candles.get(candles.size() - 2).closePrice();
    }

    private BigDecimal latestChangeAmount(List<StockChartCandleData> candles) {
        BigDecimal latestClose = latestClosePrice(candles);
        BigDecimal previousClose = previousClosePrice(candles);
        if (latestClose == null || previousClose == null) {
            return null;
        }
        return latestClose.subtract(previousClose);
    }

    private BigDecimal latestChangeRate(BigDecimal changeAmount, BigDecimal previousClosePrice) {
        if (changeAmount == null || previousClosePrice == null || previousClosePrice.signum() == 0) {
            return null;
        }
        return changeAmount
                .divide(previousClosePrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private StockChartPriceSummary chartPriceSummary(List<StockChartCandleData> candles) {
        StockChartCandleData highest = candles.stream()
                .max(Comparator.comparing(StockChartCandleData::highPrice))
                .orElse(null);
        StockChartCandleData lowest = candles.stream()
                .min(Comparator.comparing(StockChartCandleData::lowPrice))
                .orElse(null);
        if (highest == null || lowest == null) {
            return null;
        }
        return new StockChartPriceSummary(
                highest.highPrice(),
                LocalDate.parse(highest.tradeDate()),
                lowest.lowPrice(),
                LocalDate.parse(lowest.tradeDate())
        );
    }

    private boolean hasValidPrice(StockChartCandleData candle) {
        if (candle == null
                || !isPositive(candle.openPrice())
                || !isPositive(candle.highPrice())
                || !isPositive(candle.lowPrice())
                || !isPositive(candle.closePrice())) {
            return false;
        }

        BigDecimal highPrice = candle.highPrice();
        BigDecimal lowPrice = candle.lowPrice();
        BigDecimal openPrice = candle.openPrice();
        BigDecimal closePrice = candle.closePrice();
        return highPrice.compareTo(lowPrice) >= 0
                && highPrice.compareTo(openPrice) >= 0
                && highPrice.compareTo(closePrice) >= 0
                && lowPrice.compareTo(openPrice) <= 0
                && lowPrice.compareTo(closePrice) <= 0;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String priceChangeClass(BigDecimal changeAmount) {
        if (changeAmount == null || changeAmount.signum() == 0) {
            return "flat";
        }
        return changeAmount.signum() > 0 ? "bullish" : "bearish";
    }

    private int resolvePriceDecimalDigits(Stock stock) {
        return stock.getCurrency() != null && "KRW".equalsIgnoreCase(stock.getCurrency()) ? 0 : 2;
    }

    private String resolveCurrencySymbol(Stock stock) {
        if (stock.getCurrency() != null && "KRW".equalsIgnoreCase(stock.getCurrency())) {
            return "₩";
        }
        if (stock.getCurrency() != null && "USD".equalsIgnoreCase(stock.getCurrency())) {
            return "$";
        }
        return "";
    }
}
