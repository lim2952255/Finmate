package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.StockChartPeriod;
import com.finmate.domain.stock.dto.detail.StockDetailPageInfo;
import com.finmate.domain.stock.dto.detail.StockMetadataDisplayInfo;
import com.finmate.domain.stock.metadata.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import com.finmate.domain.stock.market.StockMarketSchedule;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.price.StockDailyPrice;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.metadata.DomesticStockMetadataRepository;
import com.finmate.repository.stock.metadata.OverseasStockMetadataRepository;
import com.finmate.repository.stock.price.StockDailyPriceRepository;
import com.finmate.service.stock.price.StockDailyPriceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockDetailService {
    private static final int INITIAL_HISTORY_YEARS = 3; // 기본 조회 기간
    private static final boolean DEFAULT_ADJUSTED_PRICE = true; // 수정 주가

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockDailyPriceSyncService stockDailyPriceSyncService;
    private final DomesticStockMetadataRepository domesticStockMetadataRepository;
    private final OverseasStockMetadataRepository overseasStockMetadataRepository;

    // 특정 기간동안의 특정 종목의 일봉 데이터들을 조회
    public StockDetailPageInfo getStockDetailPageInfo(Long stockId, StockChartPeriod period) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));

        // 차트 조회기간을 설정하지 않으면 기본값으로 1년으로 설정
        StockChartPeriod selectedPeriod = period == null ? StockChartPeriod.ONE_YEAR : period;
        // 현재 시간과 시장 타입을 기준으로 예상되는 최신 거래일 계산(저장되어 있어야 하는 최신 날짜)
        LocalDate expectedLatestTradeDate = getExpectedLatestTradeDate(stock.getMarketType());
        // 데이터를 가져올 시작일을 계산(최신 거래일 - 3년)
        LocalDate initialFetchStartDate = expectedLatestTradeDate.minusYears(INITIAL_HISTORY_YEARS);
        // 화면에 보여줄 차트 시작일을 계산
        LocalDate chartStartDate = selectedPeriod.getStartDate(expectedLatestTradeDate);

        // 만약 차트 시작일이 3년보다 더 오래전이면, 최대 3년전까지만 보여주도록 제한
        if (chartStartDate.isBefore(initialFetchStartDate)) {
            chartStartDate = initialFetchStartDate;
        }

        // DB에 일봉 데이터가 부족하면 KIS API를 호출하여 동기화하는 코드
        int savedDailyPriceCount = syncIfNeeded(stock, initialFetchStartDate, expectedLatestTradeDate);

        // repository에서 차트 기간만큼의 일봉 데이터를 조회
        List<StockDailyPrice> dailyPrices = stockDailyPriceRepository
                .findByStock_IdAndAdjustedPriceAndTradeDateBetweenOrderByTradeDateAsc(
                        stock.getId(),
                        DEFAULT_ADJUSTED_PRICE,
                        chartStartDate,
                        expectedLatestTradeDate);
        StockMetadataDisplayInfo metadataDisplayInfo = getMetadataDisplayInfo(stock);

        // StockDetailPageInfo라는 DTO에 dailyPrices 리스트를 담아서 리턴한다.
        return new StockDetailPageInfo(
                stock,
                selectedPeriod,
                chartStartDate,
                expectedLatestTradeDate,
                expectedLatestTradeDate,
                savedDailyPriceCount,
                dailyPrices,
                metadataDisplayInfo,
                StockMarketSchedules.isTradingTimeNow(stock.getMarketType()),
                StockMarketSchedules.tradingTimeDescription(stock.getMarketType()));
    }
    // 종목 상세 페이지를 조회하는 순간
    // 필요한 경우에만 KIS API를 호출하고
    // DB에 없는 최신 구간만 추가 저장하는 on-demand 증분 동기화 로직
    // 만약 Database에 일봉 데이터가 부족하다면, KIS API를 호출하여 데이터를 동기화하는 작업
    private int syncIfNeeded(Stock stock,
                             LocalDate initialFetchStartDate,
                             LocalDate expectedLatestTradeDate) {
        // DB에 저장된 종목의 일봉데이터중, 가장 최신 거래일의 데이터 조회
        Optional<StockDailyPrice> latestDailyPrice = stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceOrderByTradeDateDesc(
                        stock.getId(),
                        DEFAULT_ADJUSTED_PRICE);

        // 만약 조회한 데이터가 비어있으면, 아직 해당 종목의 데이터가 하나도 존재하지 않은 것이기 때문에
        // 3년치 데이터를 모두 조회해서 저장한다.
        if (latestDailyPrice.isEmpty()) {
            return stockDailyPriceSyncService.fetchAndSaveDailyPrices(
                    stock.getId(),
                    initialFetchStartDate,
                    expectedLatestTradeDate,
                    DEFAULT_ADJUSTED_PRICE);
        }

        // DB에 저장되 가상 최신 거래일이 expectedLatestTradeDate보다 더 최근이라면
        // API 호출을 통해 일봉 데이터를 받아올 필요가 없기 때문에, 바로 return한다
        LocalDate latestTradeDate = latestDailyPrice.get().getTradeDate();
        if (!latestTradeDate.isBefore(expectedLatestTradeDate)) {
            return 0;
        }

        // 가장 최신 거래일 다음 날짜부터, expectedLatesttradeDate까지의 데이터를 KIS API를 호출해서 받는다.
        LocalDate fetchStartDate = latestTradeDate.plusDays(1);
        if (fetchStartDate.isAfter(expectedLatestTradeDate)) {
            return 0;
        }

        return stockDailyPriceSyncService.fetchAndSaveDailyPrices(
                stock.getId(),
                fetchStartDate,
                expectedLatestTradeDate,
                DEFAULT_ADJUSTED_PRICE);
    }

    private StockMetadataDisplayInfo getMetadataDisplayInfo(Stock stock) {
        DomesticStockMetadata domesticMetadata = domesticStockMetadataRepository
                .findByStock_Id(stock.getId())
                .orElse(null);
        OverseasStockMetadata overseasMetadata = domesticMetadata == null
                ? overseasStockMetadataRepository.findByStock_Id(stock.getId()).orElse(null)
                : null;

        return StockMetadataDisplayInfo.from(stock, domesticMetadata, overseasMetadata);
    }

    // 만약 금일 일봉 반영 기준 시간이 지났다면 오늘 날짜까지를 ExpectedLatestTradeDate로 설정하고,
    // 그렇지 않다면 전날까지의 날짜를 ExpectedLatestTradeDate로 설정한다.
    private LocalDate getExpectedLatestTradeDate(StockMarketType marketType) {
        StockMarketSchedule schedule = StockMarketSchedules.get(marketType);
        LocalDate today = LocalDate.now(schedule.zoneId());
        LocalTime now = LocalTime.now(schedule.zoneId());

        LocalDate candidate = now.isBefore(schedule.dailyPriceAvailableTime()) ? today.minusDays(1) : today;
        return StockMarketSchedules.previousWeekday(candidate);
    }
}
