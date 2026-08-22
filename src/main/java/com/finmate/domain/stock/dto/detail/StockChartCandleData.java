package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.price.StockDailyPrice;
import com.finmate.domain.stock.price.StockPeriodPrice;

import java.math.BigDecimal;

/**
 * 프론트 차트가 직접 렌더링할 수 있도록 일봉 원본 값만 전달하는 DTO다.
 */
public record StockChartCandleData(
        String tradeDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long accumulatedVolume,
        BigDecimal accumulatedTradeAmount
) {
    public static StockChartCandleData from(StockDailyPrice dailyPrice) {
        return new StockChartCandleData(
                dailyPrice.getTradeDate().toString(),
                dailyPrice.getOpenPrice(),
                dailyPrice.getHighPrice(),
                dailyPrice.getLowPrice(),
                dailyPrice.getClosePrice(),
                dailyPrice.getAccumulatedVolume(),
                dailyPrice.getAccumulatedTradeAmount()
        );
    }

    public static StockChartCandleData from(StockPeriodPrice periodPrice) {
        return new StockChartCandleData(
                periodPrice.getCandleDate().toString(),
                periodPrice.getOpenPrice(),
                periodPrice.getHighPrice(),
                periodPrice.getLowPrice(),
                periodPrice.getClosePrice(),
                periodPrice.getAccumulatedVolume(),
                periodPrice.getAccumulatedTradeAmount()
        );
    }
}
