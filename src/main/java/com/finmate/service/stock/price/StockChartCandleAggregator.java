package com.finmate.service.stock.price;

import com.finmate.domain.stock.dto.detail.StockChartCandleData;
import com.finmate.domain.stock.price.StockDailyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// 여러개의 일봉데이터를 기반으로 기반으로 아직 확정이 되지않은 주봉/월봉/연봉 데이터를 계산한다.
// 예를들어 2026년 8월 20일(목요일)이라고 하면, 8월17일 주봉 / 8월달 월봉 / 2026년 연봉이 아직 확정되지 않은 상태이다.
// 따라서 이렇게 확정되지 않은 주봉/월봉/연봉 데이터는 DB에 저장하는것이 아니라, 저장되어있는 일봉데이터를 기반으로 계산해서 반영한다.
public final class StockChartCandleAggregator {
    private StockChartCandleAggregator() {
    }

	// 캔들 기준일과 통합할 일봉데이터를 받고, 일봉데이터를 통합한다.
    public static Optional<StockChartCandleData> aggregate(LocalDate candleDate,
                                                           List<StockDailyPrice> dailyPrices) {
        if (dailyPrices == null || dailyPrices.isEmpty()) {
            return Optional.empty();
        }
		// 거래일자를 기반으로 정렬한다.
        List<StockDailyPrice> sorted = dailyPrices.stream()
                .sorted(Comparator.comparing(StockDailyPrice::getTradeDate))
                .toList();
        return Optional.of(new StockChartCandleData(
                candleDate.toString(),
                sorted.get(0).getOpenPrice(), // 시가 계산
                sorted.stream().map(StockDailyPrice::getHighPrice).max(BigDecimal::compareTo).orElseThrow(), // 고가 계산
                sorted.stream().map(StockDailyPrice::getLowPrice).min(BigDecimal::compareTo).orElseThrow(), // 저가 계산
                sorted.get(sorted.size() - 1).getClosePrice(), // 종가 계산
                sorted.stream().mapToLong(StockDailyPrice::getAccumulatedVolume).sum(), // 거래량 계산
                sumTradeAmounts(sorted), // 거래대금 계산
                false
        ));
    }

	// 일봉데이터들의 거래대금의 합을 계산한다.
    private static BigDecimal sumTradeAmounts(List<StockDailyPrice> prices) {
		// 이때 일봉데이터중 하나라도 거래대금의 값이 null이라면 전체 결과도 null이 되도록 한다.
        if (prices.stream().anyMatch(price -> price.getAccumulatedTradeAmount() == null)) {
            return null;
        }
        List<BigDecimal> amounts = prices.stream()
                .map(StockDailyPrice::getAccumulatedTradeAmount)
                .toList();
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
