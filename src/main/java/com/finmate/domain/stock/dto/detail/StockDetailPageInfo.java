package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.price.StockDailyPrice;
import lombok.Getter;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

// 종목 상세 페이지 전체에 필요한 데이터를 모아서 view에 넘기는 DTO
@Getter
public class StockDetailPageInfo {
    private static final int RECENT_DAILY_PRICE_SIZE = 30;
    private static final int CHART_MIN_WIDTH = 940;
    private static final int CHART_HEIGHT = 540;
    private static final int CHART_LEFT_PADDING = 28;
    private static final int CHART_RIGHT_AXIS_WIDTH = 78;
    private static final int CHART_END_PADDING = 24;
    private static final int CANDLE_STEP = 5;
    private static final int PRICE_AREA_TOP = 28;
    private static final int PRICE_AREA_HEIGHT = 320;
    private static final int VOLUME_AREA_TOP = 392;
    private static final int VOLUME_AREA_HEIGHT = 82;
    private static final int DATE_AXIS_LINE_Y = 492;
    private static final int DATE_AXIS_LABEL_Y = 516;
    private static final int PRICE_AXIS_LABEL_COUNT = 5;
    private static final int DATE_AXIS_LABEL_COUNT = 5;
    private static final BigDecimal PRICE_PADDING_RATE = BigDecimal.valueOf(0.05);
    private static final DateTimeFormatter DATE_AXIS_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final Stock stock;
    private final StockChartPeriod selectedPeriod;
    private final StockChartPeriod[] chartPeriods;
    private final LocalDate chartStartDate;
    private final LocalDate chartEndDate;
    private final LocalDate expectedLatestTradeDate;
    private final LocalDate latestTradeDate;
    private final BigDecimal latestClosePrice;
    private final BigDecimal maxChartPrice;
    private final BigDecimal minChartPrice;
    private final int priceDecimalDigits;
    private final int savedDailyPriceCount;
    private final List<StockDailyPrice> dailyPrices;
    private final List<StockDailyPrice> recentDailyPrices;
    private final List<StockDailyPriceCandle> candles;
    private final int chartWidth;
    private final int chartHeight;
    private final int priceAxisX;
    private final int priceAreaTop;
    private final int priceAreaBottom;
    private final int volumeAreaTop;
    private final int volumeAreaBottom;
    private final int dateAxisLineY;
    private final int dateAxisLabelY;
    private final boolean hasLatestPriceLine;
    private final String latestPriceLineY;
    private final String realtimeCandleX;
    private final String realtimeCandleWidth;
    private final List<StockPriceAxisLabel> priceAxisLabels;
    private final List<StockDateAxisLabel> dateAxisLabels;
    private final List<StockMovingAverageLine> movingAverageLines;
    private final StockPriceMarker highestPriceMarker;
    private final StockPriceMarker lowestPriceMarker;

    public StockDetailPageInfo(Stock stock,
                               StockChartPeriod selectedPeriod,
                               LocalDate chartStartDate,
                               LocalDate chartEndDate,
                               LocalDate expectedLatestTradeDate,
                               int savedDailyPriceCount,
                               List<StockDailyPrice> dailyPrices) {
        this.stock = stock;
        this.selectedPeriod = selectedPeriod;
        this.chartPeriods = StockChartPeriod.values();
        this.chartStartDate = chartStartDate;
        this.chartEndDate = chartEndDate;
        this.expectedLatestTradeDate = expectedLatestTradeDate;
        this.savedDailyPriceCount = savedDailyPriceCount;
        this.priceDecimalDigits = resolvePriceDecimalDigits(stock);
        this.dailyPrices = dailyPrices.stream()
                .filter(this::hasValidPrice)
                .toList();
        this.recentDailyPrices = recentDailyPrices(this.dailyPrices);
        this.latestTradeDate = latestTradeDate(this.dailyPrices);
        this.latestClosePrice = latestClosePrice(this.dailyPrices);
        this.chartWidth = chartWidth(this.dailyPrices.size());
        this.chartHeight = CHART_HEIGHT;
        this.priceAxisX = chartWidth - CHART_RIGHT_AXIS_WIDTH;
        this.priceAreaTop = PRICE_AREA_TOP;
        this.priceAreaBottom = PRICE_AREA_TOP + PRICE_AREA_HEIGHT;
        this.volumeAreaTop = VOLUME_AREA_TOP;
        this.volumeAreaBottom = VOLUME_AREA_TOP + VOLUME_AREA_HEIGHT;
        this.dateAxisLineY = DATE_AXIS_LINE_Y;
        this.dateAxisLabelY = DATE_AXIS_LABEL_Y;

        BigDecimal maxPrice = maxChartPrice(this.dailyPrices);
        BigDecimal minPrice = minChartPrice(this.dailyPrices);
        this.maxChartPrice = maxPrice;
        this.minChartPrice = minPrice;
        this.candles = createCandles(this.dailyPrices, maxPrice, minPrice);
        this.hasLatestPriceLine = this.latestClosePrice != null;
        this.latestPriceLineY = this.latestClosePrice == null
                ? null
                : formatCoordinate(toPriceY(this.latestClosePrice, maxPrice, minPrice));
        this.realtimeCandleX = this.dailyPrices.isEmpty()
                ? null
                : formatCoordinate(xByIndex(this.dailyPrices.size() - 1, this.dailyPrices.size()));
        this.realtimeCandleWidth = this.dailyPrices.isEmpty()
                ? null
                : formatCoordinate(candleWidth(this.dailyPrices.size()));
        this.priceAxisLabels = createPriceAxisLabels(maxPrice, minPrice);
        this.dateAxisLabels = createDateAxisLabels(this.dailyPrices);
        this.movingAverageLines = createMovingAverageLines(this.dailyPrices, maxPrice, minPrice);
        this.highestPriceMarker = createHighestPriceMarker(this.dailyPrices, maxPrice, minPrice);
        this.lowestPriceMarker = createLowestPriceMarker(this.dailyPrices, maxPrice, minPrice);
    }

    public boolean hasDailyPrices() {
        return !dailyPrices.isEmpty();
    }

    public String formatPrice(BigDecimal value) {
        return formatDecimal(value, priceDecimalDigits);
    }

    public String formatTradeAmount(BigDecimal value) {
        return formatDecimal(value, priceDecimalDigits);
    }

    public String formatVolume(Long value) {
        if (value == null) {
            return "-";
        }

        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.KOREA);
        return numberFormat.format(value);
    }

    private List<StockDailyPrice> recentDailyPrices(List<StockDailyPrice> dailyPrices) {
        int fromIndex = Math.max(0, dailyPrices.size() - RECENT_DAILY_PRICE_SIZE);
        return dailyPrices.subList(fromIndex, dailyPrices.size());
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

    private List<StockDailyPriceCandle> createCandles(List<StockDailyPrice> dailyPrices,
                                                      BigDecimal maxPrice,
                                                      BigDecimal minPrice) {
        if (dailyPrices.isEmpty()) {
            return List.of();
        }

        long maxVolume = dailyPrices.stream()
                .map(StockDailyPrice::getAccumulatedVolume)
                .filter(volume -> volume != null && volume > 0)
                .max(Comparator.naturalOrder())
                .orElse(0L);

        List<StockDailyPriceCandle> candles = new ArrayList<>();
        double candleWidth = candleWidth(dailyPrices.size());

        for (int i = 0; i < dailyPrices.size(); i++) {
            candles.add(new StockDailyPriceCandle(
                    dailyPrices.get(i),
                    xByIndex(i, dailyPrices.size()),
                    candleWidth,
                    maxPrice,
                    minPrice,
                    maxVolume,
                    PRICE_AREA_TOP,
                    PRICE_AREA_HEIGHT,
                    VOLUME_AREA_TOP,
                    VOLUME_AREA_HEIGHT));
        }

        return candles;
    }

    private BigDecimal maxChartPrice(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return BigDecimal.ONE;
        }

        BigDecimal maxPrice = dailyPrices.stream()
                .map(StockDailyPrice::getHighPrice)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);
        BigDecimal minPrice = dailyPrices.stream()
                .map(StockDailyPrice::getLowPrice)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        return maxPrice.add(pricePadding(maxPrice, minPrice));
    }

    private BigDecimal minChartPrice(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxPrice = dailyPrices.stream()
                .map(StockDailyPrice::getHighPrice)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);
        BigDecimal minPrice = dailyPrices.stream()
                .map(StockDailyPrice::getLowPrice)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal minChartPrice = minPrice.subtract(pricePadding(maxPrice, minPrice));

        if (minChartPrice.signum() < 0) {
            return BigDecimal.ZERO;
        }

        return minChartPrice;
    }

    private BigDecimal pricePadding(BigDecimal maxPrice, BigDecimal minPrice) {
        BigDecimal range = maxPrice.subtract(minPrice);
        if (range.signum() <= 0) {
            range = maxPrice.max(BigDecimal.ONE);
        }

        return range.multiply(PRICE_PADDING_RATE);
    }

    private List<StockPriceAxisLabel> createPriceAxisLabels(BigDecimal maxPrice, BigDecimal minPrice) {
        BigDecimal range = maxPrice.subtract(minPrice);
        if (range.signum() <= 0) {
            range = BigDecimal.ONE;
        }

        List<StockPriceAxisLabel> labels = new ArrayList<>();
        for (int i = 0; i < PRICE_AXIS_LABEL_COUNT; i++) {
            BigDecimal ratio = BigDecimal.valueOf(i)
                    .divide(BigDecimal.valueOf(PRICE_AXIS_LABEL_COUNT - 1), 6, RoundingMode.HALF_UP);
            BigDecimal value = maxPrice.subtract(range.multiply(ratio));
            double y = PRICE_AREA_TOP + PRICE_AREA_HEIGHT * ratio.doubleValue();
            labels.add(new StockPriceAxisLabel(formatCoordinate(y), formatPrice(value)));
        }

        return labels;
    }

    private List<StockDateAxisLabel> createDateAxisLabels(List<StockDailyPrice> dailyPrices) {
        if (dailyPrices.isEmpty()) {
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

        List<StockDateAxisLabel> labels = new ArrayList<>();
        for (Integer index : labelIndices) {
            String textAnchor = "middle";
            if (index == 0) {
                textAnchor = "start";
            } else if (index == dailyPrices.size() - 1) {
                textAnchor = "end";
            }

            labels.add(new StockDateAxisLabel(
                    formatCoordinate(xByIndex(index, dailyPrices.size())),
                    dailyPrices.get(index).getTradeDate().format(DATE_AXIS_FORMATTER),
                    textAnchor));
        }

        return labels;
    }

    private List<StockMovingAverageLine> createMovingAverageLines(List<StockDailyPrice> dailyPrices,
                                                                  BigDecimal maxPrice,
                                                                  BigDecimal minPrice) {
        List<StockMovingAverageLine> movingAverageLines = new ArrayList<>();
        addMovingAverageLine(movingAverageLines, dailyPrices, maxPrice, minPrice, 5, "MA5", "ma5");
        addMovingAverageLine(movingAverageLines, dailyPrices, maxPrice, minPrice, 20, "MA20", "ma20");
        addMovingAverageLine(movingAverageLines, dailyPrices, maxPrice, minPrice, 60, "MA60", "ma60");
        return movingAverageLines;
    }

    private void addMovingAverageLine(List<StockMovingAverageLine> movingAverageLines,
                                      List<StockDailyPrice> dailyPrices,
                                      BigDecimal maxPrice,
                                      BigDecimal minPrice,
                                      int period,
                                      String label,
                                      String cssClass) {
        String points = movingAveragePoints(dailyPrices, maxPrice, minPrice, period);
        if (!points.isBlank()) {
            movingAverageLines.add(new StockMovingAverageLine(label, cssClass, points));
        }
    }

    private String movingAveragePoints(List<StockDailyPrice> dailyPrices,
                                       BigDecimal maxPrice,
                                       BigDecimal minPrice,
                                       int period) {
        if (dailyPrices.size() < period) {
            return "";
        }

        BigDecimal sum = BigDecimal.ZERO;
        StringJoiner points = new StringJoiner(" ");

        for (int i = 0; i < dailyPrices.size(); i++) {
            sum = sum.add(dailyPrices.get(i).getClosePrice());

            if (i >= period) {
                sum = sum.subtract(dailyPrices.get(i - period).getClosePrice());
            }

            if (i >= period - 1) {
                BigDecimal average = sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
                points.add(formatCoordinate(xByIndex(i, dailyPrices.size()))
                        + ","
                        + formatCoordinate(toPriceY(average, maxPrice, minPrice)));
            }
        }

        return points.toString();
    }

    private int chartWidth(int dailyPriceCount) {
        int calculatedWidth = CHART_LEFT_PADDING
                + Math.max(1, dailyPriceCount - 1) * CANDLE_STEP
                + CHART_END_PADDING
                + CHART_RIGHT_AXIS_WIDTH;
        return Math.max(CHART_MIN_WIDTH, calculatedWidth);
    }

    private double xByIndex(int index, int dailyPriceCount) {
        if (dailyPriceCount <= 1) {
            return CHART_LEFT_PADDING;
        }

        double plotWidth = priceAxisX - CHART_LEFT_PADDING - CHART_END_PADDING;
        return CHART_LEFT_PADDING + (plotWidth * index / (dailyPriceCount - 1));
    }

    private double candleWidth(int dailyPriceCount) {
        if (dailyPriceCount <= 1) {
            return 8;
        }

        double plotWidth = priceAxisX - CHART_LEFT_PADDING - CHART_END_PADDING;
        double step = plotWidth / (dailyPriceCount - 1);
        return Math.max(3, Math.min(9, step * 0.7));
    }

    private double toPriceY(BigDecimal price, BigDecimal maxPrice, BigDecimal minPrice) {
        double range = maxPrice.subtract(minPrice).doubleValue();
        if (range <= 0) {
            range = 1;
        }

        return PRICE_AREA_TOP + (maxPrice.subtract(price).doubleValue() / range) * PRICE_AREA_HEIGHT;
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

    private String formatDecimal(BigDecimal value, int decimalDigits) {
        if (value == null) {
            return "-";
        }

        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);
        numberFormat.setMinimumFractionDigits(0);

        numberFormat.setMaximumFractionDigits(decimalDigits);

        return numberFormat.format(value);
    }

    private int resolvePriceDecimalDigits(Stock stock) {
        if (stock.getCurrency() != null && "KRW".equalsIgnoreCase(stock.getCurrency())) {
            return 0;
        }

        return 2;
    }

    private StockPriceMarker createHighestPriceMarker(List<StockDailyPrice> dailyPrices,
                                                      BigDecimal maxPrice,
                                                      BigDecimal minPrice) {
        if (dailyPrices.isEmpty()) {
            return null;
        }

        StockDailyPrice highestDailyPrice = dailyPrices.stream()
                .max(Comparator.comparing(StockDailyPrice::getHighPrice))
                .orElse(null);
        if (highestDailyPrice == null) {
            return null;
        }

        int index = dailyPrices.indexOf(highestDailyPrice);
        return createPriceMarker(
                dailyPrices,
                index,
                highestDailyPrice.getHighPrice(),
                "최고",
                "highest",
                maxPrice,
                minPrice,
                true);
    }

    private StockPriceMarker createLowestPriceMarker(List<StockDailyPrice> dailyPrices,
                                                     BigDecimal maxPrice,
                                                     BigDecimal minPrice) {
        if (dailyPrices.isEmpty()) {
            return null;
        }

        StockDailyPrice lowestDailyPrice = dailyPrices.stream()
                .min(Comparator.comparing(StockDailyPrice::getLowPrice))
                .orElse(null);
        if (lowestDailyPrice == null) {
            return null;
        }

        int index = dailyPrices.indexOf(lowestDailyPrice);
        return createPriceMarker(
                dailyPrices,
                index,
                lowestDailyPrice.getLowPrice(),
                "최저",
                "lowest",
                maxPrice,
                minPrice,
                false);
    }

    private StockPriceMarker createPriceMarker(List<StockDailyPrice> dailyPrices,
                                               int index,
                                               BigDecimal price,
                                               String prefix,
                                               String cssClass,
                                               BigDecimal maxPrice,
                                               BigDecimal minPrice,
                                               boolean labelAbovePoint) {
        double pointX = xByIndex(index, dailyPrices.size());
        double pointY = toPriceY(price, maxPrice, minPrice);
        double plotCenterX = (CHART_LEFT_PADDING + priceAxisX - CHART_END_PADDING) / 2.0;
        boolean placeLabelLeft = pointX > plotCenterX;
        double labelX = placeLabelLeft
                ? Math.max(CHART_LEFT_PADDING + 8, pointX - 12)
                : Math.min(priceAxisX - CHART_END_PADDING - 8, pointX + 12);
        double labelY = labelAbovePoint
                ? clamp(pointY - 18, PRICE_AREA_TOP + 16, priceAreaBottom - 12)
                : clamp(pointY + 24, PRICE_AREA_TOP + 24, priceAreaBottom - 12);
        String textAnchor = placeLabelLeft ? "end" : "start";
        String formattedPrice = formatPrice(price);
        String formattedDate = dailyPrices.get(index).getTradeDate().format(DATE_AXIS_FORMATTER);
        String markerRate = formatMarkerRate(price);
        String label = prefix + " " + formattedPrice + markerRate + " - " + formattedDate;

        return new StockPriceMarker(
                formatCoordinate(pointX),
                formatCoordinate(pointY),
                formatCoordinate(labelX),
                formatCoordinate(labelY),
                textAnchor,
                label,
                cssClass,
                formattedPrice,
                formattedDate);
    }

    private String formatMarkerRate(BigDecimal markerPrice) {
        if (latestClosePrice == null || markerPrice == null || markerPrice.signum() == 0) {
            return "";
        }

        BigDecimal rate = latestClosePrice.subtract(markerPrice)
                .divide(markerPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        String sign = rate.signum() > 0 ? "+" : "";
        return " (" + sign + numberFormat.format(rate) + "%)";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
