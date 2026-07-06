package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.price.StockDailyPrice;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 일봉 하나를 SVG 캔들 하나로 그리기 위한 좌표 DTO
@Getter
public class StockDailyPriceCandle {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final double MIN_BODY_HEIGHT = 1.5;

    private final StockDailyPrice dailyPrice;
    private final String x;
    private final String wickY1;
    private final String wickY2;
    private final String bodyX;
    private final String bodyY;
    private final String bodyWidth;
    private final String bodyHeight;
    private final String volumeX;
    private final String volumeY;
    private final String volumeWidth;
    private final String volumeHeight;
    private final String hoverX;
    private final String hoverWidth;
    private final String candleType;
    private final String title;

    public StockDailyPriceCandle(StockDailyPrice dailyPrice,
                                 double x,
                                 double candleWidth,
                                 BigDecimal maxPrice,
                                 BigDecimal minPrice,
                                 long maxVolume,
                                 double priceAreaTop,
                                 double priceAreaHeight,
                                 double volumeAreaTop,
                                 double volumeAreaHeight) {
        this.dailyPrice = dailyPrice;

        double highY = toPriceY(dailyPrice.getHighPrice(), maxPrice, minPrice, priceAreaTop, priceAreaHeight);
        double lowY = toPriceY(dailyPrice.getLowPrice(), maxPrice, minPrice, priceAreaTop, priceAreaHeight);
        double openY = toPriceY(dailyPrice.getOpenPrice(), maxPrice, minPrice, priceAreaTop, priceAreaHeight);
        double closeY = toPriceY(dailyPrice.getClosePrice(), maxPrice, minPrice, priceAreaTop, priceAreaHeight);
        double calculatedBodyY = Math.min(openY, closeY);
        double calculatedBodyHeight = Math.abs(openY - closeY);
        double calculatedHoverWidth = Math.max(8, candleWidth * 2.4);

        if (calculatedBodyHeight < MIN_BODY_HEIGHT) {
            calculatedBodyY = calculatedBodyY - (MIN_BODY_HEIGHT - calculatedBodyHeight) / 2;
            calculatedBodyHeight = MIN_BODY_HEIGHT;
        }

        long volume = dailyPrice.getAccumulatedVolume() == null ? 0L : dailyPrice.getAccumulatedVolume();
        double calculatedVolumeHeight = maxVolume <= 0 ? 0 : ((double) volume / maxVolume) * volumeAreaHeight;

        this.x = formatCoordinate(x);
        this.wickY1 = formatCoordinate(highY);
        this.wickY2 = formatCoordinate(lowY);
        this.bodyX = formatCoordinate(x - candleWidth / 2);
        this.bodyY = formatCoordinate(calculatedBodyY);
        this.bodyWidth = formatCoordinate(candleWidth);
        this.bodyHeight = formatCoordinate(calculatedBodyHeight);
        this.volumeX = formatCoordinate(x - candleWidth / 2);
        this.volumeY = formatCoordinate(volumeAreaTop + volumeAreaHeight - calculatedVolumeHeight);
        this.volumeWidth = formatCoordinate(candleWidth);
        this.volumeHeight = formatCoordinate(calculatedVolumeHeight);
        this.hoverX = formatCoordinate(x - calculatedHoverWidth / 2);
        this.hoverWidth = formatCoordinate(calculatedHoverWidth);
        this.candleType = resolveCandleType(dailyPrice);
        this.title = dailyPrice.getTradeDate().format(DATE_FORMATTER)
                + " 시가 " + dailyPrice.getOpenPrice()
                + " 고가 " + dailyPrice.getHighPrice()
                + " 저가 " + dailyPrice.getLowPrice()
                + " 종가 " + dailyPrice.getClosePrice()
                + " 거래량 " + dailyPrice.getAccumulatedVolume();
    }

    private double toPriceY(BigDecimal price,
                            BigDecimal maxPrice,
                            BigDecimal minPrice,
                            double priceAreaTop,
                            double priceAreaHeight) {
        double range = maxPrice.subtract(minPrice).doubleValue();
        if (range <= 0) {
            range = 1;
        }

        return priceAreaTop + (maxPrice.subtract(price).doubleValue() / range) * priceAreaHeight;
    }

    private String resolveCandleType(StockDailyPrice dailyPrice) {
        int compare = dailyPrice.getClosePrice().compareTo(dailyPrice.getOpenPrice());
        if (compare > 0) {
            return "bullish";
        }

        if (compare < 0) {
            return "bearish";
        }

        return "flat";
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
