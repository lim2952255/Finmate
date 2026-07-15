package com.finmate.service.market;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorSchedule;
import com.finmate.domain.market.MarketIndicatorSchedules;
import com.finmate.domain.market.MarketIndicatorType;
import com.finmate.domain.market.dto.MarketDataChartPeriod;
import com.finmate.domain.market.dto.MarketIndicatorPageInfo;
import com.finmate.domain.market.price.MarketDailyPrice;
import com.finmate.repository.market.price.MarketDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// 환율 / 주가지수 페이지에 필요한 데이터들을 KIS API를 통해서 얻고, 이를 DB에 저장하고, 환율 / 주가지수 상세페이지에 필요한 데이터들을 DTO에 담는 역할을 수행한다.
@Service
@RequiredArgsConstructor
public class MarketDataService {
    private static final int INITIAL_HISTORY_YEARS = 3;

    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final MarketDailyPriceSyncService marketDailyPriceSyncService; // KIS API를 호출하여 환율 / 주가지수 데이터를 얻고, 이를 DB에 저장하는 서비스

    public MarketIndicatorPageInfo getMarketIndicatorPageInfo(MarketIndicatorType indicatorType, // 환율인지, 주가지수인지를 구분하는 카테고리
                                                              MarketIndicatorSymbol indicator, // USD/KRW, 코스피, 코스닥, 나스닥 종합 같은 실제 조회 대상
                                                              MarketDataChartPeriod period) {
        // indicator가 null이면 indicatorType을 기반으로 DefaultIndicatorSymbol을 설정
        MarketIndicatorSymbol selectedIndicator = indicator == null
                ? MarketIndicatorSymbol.defaultFor(indicatorType)
                : indicator;
        if (selectedIndicator.getType() != indicatorType) {
            throw new RuntimeException("요청한 화면에서 조회할 수 없는 지수/환율입니다.");
        }
        // 조회기간은 기본값은 1년
        MarketDataChartPeriod selectedPeriod = period == null ? MarketDataChartPeriod.ONE_YEAR : period;
        // 최신으로 기대되는 거래일 계산
        LocalDate expectedLatestTradeDate = getExpectedLatestTradeDate(selectedIndicator);
        // 데이터 저장 기간의 가본값은 3년(expectedLatestTradeDate - 3년 ~ expectedLatestTradeDate)
        LocalDate initialFetchStartDate = expectedLatestTradeDate.minusYears(INITIAL_HISTORY_YEARS);

        // 차트 시작일 계산(사용자가 선택한 기간을 기준으로 실제 화면에 보여줄 차트 시작일을 계산한다.)
        LocalDate chartStartDate = selectedPeriod.getStartDate(expectedLatestTradeDate);
        if (chartStartDate.isBefore(initialFetchStartDate)) {
            chartStartDate = initialFetchStartDate; // 차트 시작일이 initialFetchStartDate보다 더 이전이면 chartStartDate를 initialFetchStartDate로 설정
        }
        // 현재 DB에 최신 데이터까지 update가 되어있는지를 확인하고, 부족하면, 부족한 만큼만 on-demand방식으로 KIS API에 데이터를 요청한다.
        int savedDailyPriceCount = syncIfNeeded(selectedIndicator, initialFetchStartDate, expectedLatestTradeDate);

        // DB에서 실제 차트에 보여줄 일봉 데이터를 조회한다.
        List<MarketDailyPrice> dailyPrices = marketDailyPriceRepository
                .findByIndicatorTypeAndIndicatorSymbolAndTradeDateBetweenOrderByTradeDateAsc(
                        selectedIndicator.getType(),
                        selectedIndicator.getKisSymbol(),
                        chartStartDate,
                        expectedLatestTradeDate);

        return new MarketIndicatorPageInfo(
                indicatorType,
                selectedIndicator,
                MarketIndicatorSymbol.findByType(indicatorType),
                selectedPeriod,
                chartStartDate,
                expectedLatestTradeDate,
                savedDailyPriceCount,
                dailyPrices);
    }
    // 현재 DB에 차트에 필요한 최신 데이터가 저장되어 있는지 확인하고, 부족한 부분만큼만 KIS API를 호출하여 데이터를 받아오는 메서드
    private int syncIfNeeded(MarketIndicatorSymbol indicator,
                             LocalDate initialFetchStartDate,
                             LocalDate expectedLatestTradeDate) {
        // DB에서 가장 최신 데이터를 찾는다.
        Optional<MarketDailyPrice> latestDailyPrice = marketDailyPriceRepository
                .findTopByIndicatorTypeAndIndicatorSymbolOrderByTradeDateDesc(
                        indicator.getType(),
                        indicator.getKisSymbol());

        if (latestDailyPrice.isEmpty()) { // 만약 데이터가 하나도 존재하지 않으면, 3년치 데이터를 모두 요청하고, 저장한다.
            return marketDailyPriceSyncService.fetchAndSaveDailyPrices(
                    indicator,
                    initialFetchStartDate,
                    expectedLatestTradeDate);
        }

        LocalDate latestTradeDate = latestDailyPrice.get().getTradeDate();
        if (!latestTradeDate.isBefore(expectedLatestTradeDate)) { // 만약 DB에 저장되어 있는 최신 데이터가, 조회하고자하는 차트의 기대 최신일보다 더 최신값이라면 DB update를 하지 않는다.
            return 0;
        }

        // KIS API를 통해 데이터를 받을 시작날짜(현재 DB에 저장된 최신 데이터의 다음날짜)를 설정한다.
        LocalDate fetchStartDate = latestTradeDate.plusDays(1);
        if (fetchStartDate.isAfter(expectedLatestTradeDate)) { // 만약 시작날짜가 차트의 기대 최신일보다 더 최신이라면 API요청을 하지 않는다.
            return 0;
        }
        // 차트 조회를 위해 부족한 데이터 일수만큼만 KIS API를 호출하여 데이터를 받아서 저장한다.
        return marketDailyPriceSyncService.fetchAndSaveDailyPrices(
                indicator,
                fetchStartDate,
                expectedLatestTradeDate);
    }

    // 최신으로 기대되는 거래일 계산
    private LocalDate getExpectedLatestTradeDate(MarketIndicatorSymbol indicator) {
        MarketIndicatorSchedule schedule = MarketIndicatorSchedules.get(indicator); // 장 마감시간
        LocalDate today = LocalDate.now(schedule.zoneId()); // 현재 날짜
        LocalTime now = LocalTime.now(schedule.zoneId()); // 현재 시간

        // 현재 시간이 금일 장 마감시간 이전이라면 현재 날짜 -1로 계산
        LocalDate candidate = now.isBefore(schedule.dailyPriceAvailableTime()) ? today.minusDays(1) : today;
        return MarketIndicatorSchedules.previousWeekday(candidate); // 주말은 제외
    }
}
