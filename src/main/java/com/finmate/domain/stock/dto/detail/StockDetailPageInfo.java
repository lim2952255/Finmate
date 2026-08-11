package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.price.StockDailyPrice;
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
    private final StockChartPeriod selectedPeriod;
    private final StockChartPeriod[] chartPeriods;
    private final LocalDate chartStartDate;
    private final LocalDate chartEndDate;
    private final LocalDate latestTradeDate;
    private final BigDecimal latestClosePrice;
    private final BigDecimal latestChangeAmount;
    private final BigDecimal latestChangeRate;
    private final String latestPriceChangeClass;
    private final String currencySymbol;
    private final int priceDecimalDigits;
    private final int savedDailyPriceCount;
    private final List<StockChartCandleData> chartCandles;
    private final StockChartPriceSummary chartPriceSummary;
    private final StockMetadataDisplayInfo metadataDisplayInfo;
    private final DomesticStockDetailInfo domesticDetailInfo;
    private final boolean stockTradingAvailable;
    private final String stockTradingTimeDescription;

    public StockDetailPageInfo(Stock stock,
                               StockChartPeriod selectedPeriod,
                               LocalDate chartStartDate,
                               LocalDate chartEndDate,
                               int savedDailyPriceCount,
                               List<StockDailyPrice> dailyPrices,
                               StockMetadataDisplayInfo metadataDisplayInfo,
                               DomesticStockDetailInfo domesticDetailInfo,
                               boolean stockTradingAvailable,
                               String stockTradingTimeDescription) {
        this.stock = stock;
        this.selectedPeriod = selectedPeriod;
        this.chartPeriods = StockChartPeriod.values();
        this.chartStartDate = chartStartDate;
        this.chartEndDate = chartEndDate;
        this.savedDailyPriceCount = savedDailyPriceCount;
        this.currencySymbol = resolveCurrencySymbol(stock);
        this.priceDecimalDigits = resolvePriceDecimalDigits(stock);

        List<StockDailyPrice> validDailyPrices = dailyPrices.stream()
                .filter(this::hasValidPrice)
                .toList();
        this.chartCandles = validDailyPrices.stream()
                .map(StockChartCandleData::from)
                .toList();
        this.latestTradeDate = latestTradeDate(validDailyPrices);
        this.latestClosePrice = latestClosePrice(validDailyPrices);
        this.latestChangeAmount = latestChangeAmount(validDailyPrices);
        this.latestChangeRate = latestChangeRate(this.latestChangeAmount, previousClosePrice(validDailyPrices));
        this.latestPriceChangeClass = priceChangeClass(this.latestChangeAmount);
        this.chartPriceSummary = chartPriceSummary(validDailyPrices);
        this.metadataDisplayInfo = metadataDisplayInfo;
        this.domesticDetailInfo = domesticDetailInfo;
        this.stockTradingAvailable = stockTradingAvailable;
        this.stockTradingTimeDescription = stockTradingTimeDescription;
    }

    public boolean hasDailyPrices() {
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

    private LocalDate latestTradeDate(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return null;
        }
        return dailyPrices.get(dailyPrices.size() - 1).getTradeDate();
    }

    private BigDecimal latestClosePrice(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return null;
        }
        return dailyPrices.get(dailyPrices.size() - 1).getClosePrice();
    }

    private BigDecimal previousClosePrice(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.size() < 2) {
            return null;
        }
        return dailyPrices.get(dailyPrices.size() - 2).getClosePrice();
    }

    private BigDecimal latestChangeAmount(List<StockDailyPrice> dailyPrices) {
        BigDecimal latestClose = latestClosePrice(dailyPrices);
        BigDecimal previousClose = previousClosePrice(dailyPrices);
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

    private StockChartPriceSummary chartPriceSummary(List<StockDailyPrice> dailyPrices) {
        StockDailyPrice highest = dailyPrices.stream()
                .max(Comparator.comparing(StockDailyPrice::getHighPrice))
                .orElse(null);
        StockDailyPrice lowest = dailyPrices.stream()
                .min(Comparator.comparing(StockDailyPrice::getLowPrice))
                .orElse(null);
        if (highest == null || lowest == null) {
            return null;
        }
        return new StockChartPriceSummary(
                highest.getHighPrice(),
                highest.getTradeDate(),
                lowest.getLowPrice(),
                lowest.getTradeDate()
        );
    }

    private boolean hasValidPrice(StockDailyPrice dailyPrice) {
        if (dailyPrice == null
                || !isPositive(dailyPrice.getOpenPrice())
                || !isPositive(dailyPrice.getHighPrice())
                || !isPositive(dailyPrice.getLowPrice())
                || !isPositive(dailyPrice.getClosePrice())) {
            return false;
        }

        BigDecimal highPrice = dailyPrice.getHighPrice();
        BigDecimal lowPrice = dailyPrice.getLowPrice();
        BigDecimal openPrice = dailyPrice.getOpenPrice();
        BigDecimal closePrice = dailyPrice.getClosePrice();
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
