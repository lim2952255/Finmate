package com.finmate.domain.market.dto;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorType;
import com.finmate.domain.market.price.MarketDailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record MarketIndicatorPageInfo(
        MarketIndicatorType indicatorType,
        MarketIndicatorSymbol selectedIndicator,
        List<MarketIndicatorSymbol> indicators,
        MarketDataChartPeriod selectedPeriod,
        LocalDate chartStartDate,
        LocalDate expectedLatestTradeDate,
        int savedDailyPriceCount,
        List<MarketDailyPrice> dailyPrices
) {
    private static final int RECENT_DAILY_PRICE_SIZE = 30;
    private static final int CHART_WIDTH = 920;
    private static final int CHART_HEIGHT = 430;
    private static final int CHART_LEFT_PADDING = 36;
    private static final int CHART_RIGHT_PADDING = 84;
    private static final int CHART_TOP_PADDING = 30;
    private static final int CHART_BOTTOM_PADDING = 42;
    private static final int DATE_AXIS_LABEL_COUNT = 5;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_AXIS_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public boolean hasDailyPrices() {
        return dailyPrices != null && !dailyPrices.isEmpty();
    }

    public LocalDate latestTradeDate() {
        if (!hasDailyPrices()) {
            return null;
        }

        return dailyPrices.get(dailyPrices.size() - 1).getTradeDate();
    }

    public BigDecimal latestClosePrice() {
        if (!hasDailyPrices()) {
            return null;
        }

        return dailyPrices.get(dailyPrices.size() - 1).getClosePrice();
    }

    public BigDecimal previousClosePrice() {
        if (dailyPrices == null || dailyPrices.size() < 2) {
            return null;
        }

        return dailyPrices.get(dailyPrices.size() - 2).getClosePrice();
    }

    public BigDecimal changeAmount() {
        BigDecimal latestClosePrice = latestClosePrice();
        BigDecimal previousClosePrice = previousClosePrice();
        if (latestClosePrice == null || previousClosePrice == null) {
            return null;
        }

        return latestClosePrice.subtract(previousClosePrice);
    }

    public BigDecimal changeRate() {
        BigDecimal previousClosePrice = previousClosePrice();
        if (previousClosePrice == null || previousClosePrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return changeAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClosePrice, 2, RoundingMode.HALF_UP);
    }

    public String priceChangeClass() {
        BigDecimal changeAmount = changeAmount();
        if (changeAmount == null || changeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "flat";
        }

        return changeAmount.signum() > 0 ? "bullish" : "bearish";
    }

    public List<MarketDailyPrice> recentDailyPrices() {
        if (!hasDailyPrices()) {
            return List.of();
        }

        int fromIndex = Math.max(0, dailyPrices.size() - RECENT_DAILY_PRICE_SIZE);
        List<MarketDailyPrice> recentPrices = new ArrayList<>(dailyPrices.subList(fromIndex, dailyPrices.size()));
        Collections.reverse(recentPrices);
        return recentPrices;
    }

    public String chartPolylinePoints() {
        if (!hasDailyPrices()) {
            return "";
        }

        return chartPoints().stream()
                .map(MarketChartPoint::coordinate)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    public List<MarketChartPoint> chartPoints() {
        if (!hasDailyPrices()) {
            return List.of();
        }

        BigDecimal maxPrice = maxClosePrice();
        BigDecimal minPrice = minClosePrice();
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        int count = dailyPrices.size();
        int plotHeight = chartPlotHeight();
        double hoverWidth = chartHoverWidth(count);

        List<MarketChartPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MarketDailyPrice dailyPrice = dailyPrices.get(i);
            double x = xByIndex(i, count);
            double y = yCoordinate(dailyPrice.getClosePrice(), maxPrice, priceRange, plotHeight);
            String formattedDate = formatDate(dailyPrice.getTradeDate());
            String formattedOpenPrice = formatPrice(dailyPrice.getOpenPrice());
            String formattedHighPrice = formatPrice(dailyPrice.getHighPrice());
            String formattedLowPrice = formatPrice(dailyPrice.getLowPrice());
            String formattedClosePrice = formatPrice(dailyPrice.getClosePrice());
            String formattedVolume = formatVolume(dailyPrice.getAccumulatedVolume());
            points.add(new MarketChartPoint(
                    formatCoordinate(x),
                    formatCoordinate(y),
                    formatCoordinate(x - hoverWidth / 2),
                    formatCoordinate(hoverWidth),
                    formattedDate,
                    formattedOpenPrice,
                    formattedHighPrice,
                    formattedLowPrice,
                    formattedClosePrice,
                    formattedVolume,
                    dailyPrice.getClosePrice(),
                    formattedDate
                            + " 시가 " + formattedOpenPrice
                            + " 고가 " + formattedHighPrice
                            + " 저가 " + formattedLowPrice
                            + " 종가 " + formattedClosePrice));
        }

        return points;
    }

    public List<MarketDateAxisLabel> dateAxisLabels() {
        if (!hasDailyPrices()) {
            return List.of();
        }

        int labelCount = Math.min(DATE_AXIS_LABEL_COUNT, dailyPrices.size());
        Set<Integer> labelIndices = new LinkedHashSet<>();

        if (labelCount == 1) {
            labelIndices.add(0);
        } else {
            for (int i = 0; i < labelCount; i++) {
                int index = (int) Math.round((double) (dailyPrices.size() - 1) * i / (labelCount - 1));
                labelIndices.add(index);
            }
        }

        List<MarketDateAxisLabel> labels = new ArrayList<>();
        for (Integer index : labelIndices) {
            String textAnchor = "middle";
            if (dailyPrices.size() > 1 && index == 0) {
                textAnchor = "start";
            } else if (dailyPrices.size() > 1 && index == dailyPrices.size() - 1) {
                textAnchor = "end";
            }

            labels.add(new MarketDateAxisLabel(
                    formatCoordinate(xByIndex(index, dailyPrices.size())),
                    dailyPrices.get(index).getTradeDate().format(DATE_AXIS_FORMATTER),
                    textAnchor));
        }

        return labels;
    }

    public boolean hasLatestPriceLine() {
        return latestClosePrice() != null;
    }

    public String latestPriceLineY() {
        Double position = latestPriceY();
        return position == null ? null : formatCoordinate(position);
    }

    public String latestPriceBadgeY() {
        Double position = latestPriceY();
        return position == null ? null : formatCoordinate(position - 18);
    }

    public String latestPriceBadgePriceY() {
        Double position = latestPriceY();
        return position == null ? null : formatCoordinate(position - 4);
    }

    public String latestPriceBadgeRateY() {
        Double position = latestPriceY();
        return position == null ? null : formatCoordinate(position + 11);
    }

    public int dateAxisLineY() {
        return chartBottom();
    }

    public int dateAxisLabelY() {
        return CHART_HEIGHT - 14;
    }

    public BigDecimal minClosePrice() {
        if (!hasDailyPrices()) {
            return BigDecimal.ZERO;
        }

        return dailyPrices.stream()
                .map(MarketDailyPrice::getClosePrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal maxClosePrice() {
        if (!hasDailyPrices()) {
            return BigDecimal.ZERO;
        }

        return dailyPrices.stream()
                .map(MarketDailyPrice::getClosePrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal middleClosePrice() {
        if (!hasDailyPrices()) {
            return BigDecimal.ZERO;
        }

        return maxClosePrice()
                .add(minClosePrice())
                .divide(BigDecimal.valueOf(2), selectedIndicator.getFractionDigits(), RoundingMode.HALF_UP);
    }

    public String formatLatestClosePrice() {
        return formatPrice(latestClosePrice());
    }

    public String formatPrice(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(selectedIndicator.getFractionDigits());
        formatter.setMaximumFractionDigits(selectedIndicator.getFractionDigits());
        return formatter.format(value);
    }

    public String formatSignedChangeAmount() {
        BigDecimal changeAmount = changeAmount();
        if (changeAmount == null) {
            return "-";
        }

        String formattedAmount = formatPrice(changeAmount.abs());
        if (changeAmount.signum() > 0) {
            return "+" + formattedAmount;
        }

        if (changeAmount.signum() < 0) {
            return "-" + formattedAmount;
        }

        return formattedAmount;
    }

    public String formatSignedChangeRate() {
        BigDecimal changeRate = changeRate();
        if (changeRate == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        String formattedRate = formatter.format(changeRate.abs()) + "%";
        if (changeRate.signum() > 0) {
            return "+" + formattedRate;
        }

        if (changeRate.signum() < 0) {
            return "-" + formattedRate;
        }

        return formattedRate;
    }

    public String formatVolume(Long value) {
        if (value == null) {
            return "-";
        }

        return NumberFormat.getIntegerInstance(Locale.KOREA).format(value);
    }

    public String formatDate(LocalDate value) {
        if (value == null) {
            return "-";
        }

        return value.format(DATE_FORMATTER);
    }

    public int chartWidth() {
        return CHART_WIDTH;
    }

    public int chartHeight() {
        return CHART_HEIGHT;
    }

    public int chartLeft() {
        return CHART_LEFT_PADDING;
    }

    public int chartRight() {
        return CHART_WIDTH - CHART_RIGHT_PADDING;
    }

    public int chartTop() {
        return CHART_TOP_PADDING;
    }

    public int chartBottom() {
        return CHART_HEIGHT - CHART_BOTTOM_PADDING;
    }

    public int chartPlotWidth() {
        return chartRight() - chartLeft();
    }

    public int chartPlotHeight() {
        return chartBottom() - chartTop();
    }

    public int chartMiddleY() {
        return chartTop() + (chartPlotHeight() / 2);
    }

    private Double latestPriceY() {
        BigDecimal latestClosePrice = latestClosePrice();
        if (latestClosePrice == null) {
            return null;
        }

        BigDecimal maxPrice = maxClosePrice();
        BigDecimal minPrice = minClosePrice();
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        return yCoordinate(latestClosePrice, maxPrice, priceRange, chartPlotHeight());
    }

    private double xByIndex(int index, int count) {
        if (count <= 1) {
            return chartLeft() + chartPlotWidth() / 2.0;
        }

        return chartLeft() + ((double) chartPlotWidth() * index / (count - 1));
    }

    private double chartHoverWidth(int count) {
        if (count <= 1) {
            return chartPlotWidth();
        }

        double step = (double) chartPlotWidth() / (count - 1);
        return Math.max(10, step);
    }

    private double yCoordinate(BigDecimal price,
                               BigDecimal maxPrice,
                               BigDecimal priceRange,
                               int plotHeight) {
        if (priceRange.compareTo(BigDecimal.ZERO) == 0) {
            return CHART_HEIGHT / 2.0;
        }

        BigDecimal distanceFromMax = maxPrice.subtract(price);
        double ratio = distanceFromMax.divide(priceRange, 8, RoundingMode.HALF_UP).doubleValue();
        return CHART_TOP_PADDING + (plotHeight * ratio);
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public record MarketChartPoint(
            String x,
            String y,
            String hoverX,
            String hoverWidth,
            String tradeDate,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume,
            BigDecimal rawClosePrice,
            String title
    ) {
        public String coordinate() {
            return x + "," + y;
        }
    }

    public record MarketDateAxisLabel(
            String x,
            String displayValue,
            String textAnchor
    ) {
    }
}
