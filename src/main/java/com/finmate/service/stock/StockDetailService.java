package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.StockChartCandleData;
import com.finmate.domain.stock.dto.detail.StockChartInterval;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo;
import com.finmate.domain.stock.dto.detail.StockDetailPageInfo;
import com.finmate.domain.stock.dto.detail.StockIndustryDisplayNames;
import com.finmate.domain.stock.dto.detail.StockMetadataDisplayInfo;
import com.finmate.domain.stock.metadata.domestic.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.overseas.OverseasStockMetadata;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.price.StockDailyPrice;
import com.finmate.domain.stock.price.StockPeriodPrice;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.metadata.DomesticStockMetadataRepository;
import com.finmate.repository.stock.metadata.OverseasStockMetadataRepository;
import com.finmate.repository.stock.price.StockDailyPriceRepository;
import com.finmate.repository.stock.price.StockPeriodPriceRepository;
import com.finmate.service.stock.price.StockChartCandleAggregator;
import com.finmate.service.stock.price.StockDailyPriceSyncService;
import com.finmate.service.stock.price.StockPeriodPriceSyncService;
import com.finmate.service.stock.price.StockMinuteChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockDetailService {
    private static final int INITIAL_HISTORY_YEARS = 3; // 기본 조회 기간
    private static final int WEEK_HISTORY_YEARS = 10;
    private static final LocalDate UNKNOWN_LISTED_DATE_FALLBACK = LocalDate.of(1900, 1, 1);
    private static final int HISTORY_START_TOLERANCE_DAYS = 7; // 주말·휴장일을 포함한 최초 거래일 허용 범위
    private static final boolean DEFAULT_ADJUSTED_PRICE = true; // 수정 주가

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockPeriodPriceRepository stockPeriodPriceRepository;
    private final StockDailyPriceSyncService stockDailyPriceSyncService; // 일봉 데이터를 동기화하는 서비스
    private final StockPeriodPriceSyncService stockPeriodPriceSyncService; // 주봉 / 월봉 / 연봉 데이터를 동기화하는 서비스
    private final StockMinuteChartService stockMinuteChartService;
    private final StockIndustryCodeService stockIndustryCodeService;
    private final DomesticStockMetadataRepository domesticStockMetadataRepository;
    private final OverseasStockMetadataRepository overseasStockMetadataRepository;
    private final DomesticStockDetailRefreshService domesticStockDetailRefreshService;
    private final DomesticStockDetailQueryService domesticStockDetailQueryService;

	// 종목 상세정보에서 필요한 정보들을 DTO에 담아서 리턴한다.
    public StockDetailPageInfo getStockDetailPageInfo(Long stockId, StockChartInterval interval) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));

        StockChartInterval selectedInterval = interval == null ? StockChartInterval.DAY : interval;
        LocalDate expectedLatestTradeDate = StockMarketSchedules.expectedLatestDailyPriceTradeDate(stock.getMarketType());
        LocalDate marketDate = LocalDate.now(StockMarketSchedules.getSchedule(stock.getMarketType()).zoneId());
        LocalDate dailyHistoryStartDate = resolveHistoryStartDate(
                stock, expectedLatestTradeDate.minusYears(INITIAL_HISTORY_YEARS));
        LocalDate dailySyncStartDate = selectedInterval == StockChartInterval.DAY
                ? dailyHistoryStartDate
                : selectedInterval.periodStart(marketDate);

        int savedPriceCount = selectedInterval.isMinute()
                ? 0
                : syncDailyIfNeeded(stock, dailySyncStartDate, expectedLatestTradeDate);

        LocalDate chartStartDate;
        LocalDate chartEndDate;
        List<StockChartCandleData> chartCandles;
        Long currentCandleBaseVolume = 0L;
        BigDecimal currentCandleBaseTradeAmount = BigDecimal.ZERO;
        if (selectedInterval.isMinute()) {
            chartCandles = stockMinuteChartService.getCandles(stock, selectedInterval); // Redis에서 혹은 KIS API를 호출해서 분봉데이터를 조회한다.
            chartStartDate = candleDate(chartCandles, 0, marketDate);
            chartEndDate = candleDate(chartCandles, chartCandles.size() - 1, marketDate);
        } else if (selectedInterval == StockChartInterval.DAY) {
            chartStartDate = dailyHistoryStartDate;
            chartEndDate = expectedLatestTradeDate;
            chartCandles = stockDailyPriceRepository
                    .findByStock_IdAndAdjustedPriceAndTradeDateBetweenOrderByTradeDateAsc(
                            stock.getId(), DEFAULT_ADJUSTED_PRICE, chartStartDate, chartEndDate)
                    .stream()
                    .map(StockChartCandleData::from)
                    .toList();
        } else {
            chartStartDate = resolveIntervalHistoryStartDate(stock, selectedInterval, marketDate);
            chartEndDate = marketDate;
            LocalDate completedPeriodEnd = selectedInterval.previousCompletedPeriodEnd(marketDate);
            savedPriceCount += syncPeriodIfNeeded(
                    stock, selectedInterval, chartStartDate, completedPeriodEnd);
            PeriodChartLoadResult loadResult = loadPeriodCandles(
                    stock, selectedInterval, chartStartDate, completedPeriodEnd, expectedLatestTradeDate, marketDate);
            chartCandles = loadResult.candles();
            currentCandleBaseVolume = loadResult.realtimeBaseVolume();
            currentCandleBaseTradeAmount = loadResult.realtimeBaseTradeAmount();
        }
        List<StockChartCandleData> latestDailyCandles = loadLatestDailyCandles(stock);
        StockMetadataDisplayInfo metadataDisplayInfo = getMetadataDisplayInfo(stock);
        DomesticStockDetailInfo domesticDetailInfo = getDomesticDetailInfo(stock);

        return new StockDetailPageInfo(
                stock,
                selectedInterval,
                chartStartDate,
                chartEndDate,
                savedPriceCount,
                chartCandles,
                latestDailyCandles,
                marketDate,
                currentCandleBaseVolume,
                currentCandleBaseTradeAmount,
                metadataDisplayInfo,
                domesticDetailInfo,
                StockMarketSchedules.isTradingTimeNow(stock),
                StockMarketSchedules.describeTradingHours(stock));
    }

    // 국내 종목의 상세정보. 즉 재무상태표나 대차대조표 등의 정보를 DomesticStockDetailInfo라는 하나의 DTO에 담아서 리턴한다.
    private DomesticStockDetailInfo getDomesticDetailInfo(Stock stock) {
        if (stock.getMarketType() != StockMarketType.KOSPI
                && stock.getMarketType() != StockMarketType.KOSDAQ) {
            return DomesticStockDetailInfo.unsupported();
        }

        // 종목 현재가는 Redis에 캐싱된 데이터가 있으면 캐싱된 데이터를 리턴받고, 없으면 KIS API를 통해 갱신한 다음, 데이터를 Redis에 캐싱하고 캐싱된 데이터를 받는다.
        // 다른 데이터들은 DB에서 정보를 조회해서 데이터를 최신상태로 만든다.
        DomesticStockCurrentQuoteSnapshot currentQuote =
                domesticStockDetailRefreshService.refreshIfNeeded(stock);
        // 현재가는 Redis에 캐싱하거나 캐싱되어있는 데이터를 전달하고, 없으면 DB에서 가장 최신 데이터를 조회해서 DTO를 만든다.
        // 다른 데이터들은 DB에서 가장 최신 데이터를 조회해서 DTO를 만든다.
        return domesticStockDetailQueryService.getDetailInfo(stock, currentQuote);
    }
    // 종목 상세 페이지를 조회하는 순간
    // 필요한 경우에만 KIS API를 호출하고
    // DB에 없는 최신 구간만 추가 저장하는 on-demand 증분 동기화 로직
    // 만약 Database에 일봉 데이터가 부족하다면, KIS API를 호출하여 데이터를 동기화하는 작업
    private int syncDailyIfNeeded(Stock stock,
                                  LocalDate initialFetchStartDate,
                                  LocalDate expectedLatestTradeDate) {
        if (initialFetchStartDate.isAfter(expectedLatestTradeDate)) {
            return 0;
        }
        LocalDate historyStartDate = resolveHistoryStartDate(stock, initialFetchStartDate);
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
                    historyStartDate,
                    expectedLatestTradeDate,
                    DEFAULT_ADJUSTED_PRICE);
        }

        int savedCount = backfillHistoricalDailyPricesIfNeeded(
                stock,
                historyStartDate,
                latestDailyPrice.get());

        // DB에 저장되 가상 최신 거래일이 expectedLatestTradeDate보다 더 최근이라면
        // API 호출을 통해 일봉 데이터를 받아올 필요가 없기 때문에, 바로 return한다
        LocalDate latestTradeDate = latestDailyPrice.get().getTradeDate();
        if (!latestTradeDate.isBefore(expectedLatestTradeDate)) {
            return savedCount;
        }

        // 가장 최신 거래일 다음 날짜부터, expectedLatesttradeDate까지의 데이터를 KIS API를 호출해서 받는다.
        LocalDate fetchStartDate = latestTradeDate.plusDays(1);
        if (fetchStartDate.isAfter(expectedLatestTradeDate)) {
            return savedCount;
        }

        return savedCount + stockDailyPriceSyncService.fetchAndSaveDailyPrices(
                stock.getId(),
                fetchStartDate,
                expectedLatestTradeDate,
                DEFAULT_ADJUSTED_PRICE);
    }

    // 주식 일봉데이터가 3년치 데이터가 저장되어 있는지를 검사한다. 만약 부족하면 부족한 일자만큼의 데이터를 채운다.
    private int backfillHistoricalDailyPricesIfNeeded(Stock stock,
                                                       LocalDate historyStartDate, // 과거 3년전 시점
                                                       StockDailyPrice latestDailyPrice) {
        // 현재 DB에서 저장된 데이터중 가장 오래된 데이터를 조회한다.
        StockDailyPrice oldestDailyPrice = stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceOrderByTradeDateAsc(
                        stock.getId(), DEFAULT_ADJUSTED_PRICE)
                .orElse(latestDailyPrice);
        //  3년점 시점의 데이터의 허용 오차 적용 (+7일)
        LocalDate coveredHistoryStartDate = historyStartDate.plusDays(HISTORY_START_TOLERANCE_DAYS);
        // 만약 기준 일자보다 이미 DB에 저장되어 있는 데이터의 일자가 더 오래되어 있다면 바로 리턴한다.
        if (!oldestDailyPrice.getTradeDate().isAfter(coveredHistoryStartDate)) {
            return 0;
        }

        // 가장 오래된 데이터의 일자와 기준 일자를 비교해서 부족한 만큼 API를 호출해서 데이터를 저장한다.
        LocalDate backfillEndDate = oldestDailyPrice.getTradeDate().minusDays(1);
        return stockDailyPriceSyncService.fetchAndSaveDailyPrices(
                stock.getId(),
                historyStartDate,
                backfillEndDate,
                DEFAULT_ADJUSTED_PRICE);
    }

	// 종목 상세 페이지를 조회하는 순간
	// 필요한 경우에만 KIS API를 호출하고
	// DB에 없는 최신 구간만 추가 저장하는 on-demand 증분 동기화 로직
	// 만약 Database에 주봉/월봉/연봉 데이터가 부족하다면, KIS API를 호출하여 데이터를 동기화하는 작업
    private int syncPeriodIfNeeded(Stock stock,
                                   StockChartInterval interval,
                                   LocalDate historyStartDate,
                                   LocalDate completedPeriodEnd) {
        if (historyStartDate.isAfter(completedPeriodEnd)) {
            return 0;
        }
		// DB에 저장되어 있는 기간봉의 가장 최근 기준일의 데이터를 조회한다.
        Optional<StockPeriodPrice> latest = stockPeriodPriceRepository
                .findTopByStock_IdAndIntervalAndAdjustedPriceOrderByCandleDateDesc(
                        stock.getId(), interval, DEFAULT_ADJUSTED_PRICE);
		// 만약 DB에 데이터가 한건도 저장되어 있지 않으면 전체 기간의 데이터를 조회한다.
        if (latest.isEmpty()) {
            return stockPeriodPriceSyncService.fetchAndSavePeriodPrices(
                    stock.getId(), interval, historyStartDate, completedPeriodEnd, DEFAULT_ADJUSTED_PRICE);
        }
		// 만약 2020~2026 데이터가 필요한데, DB에는 2024~2026데이터만 저장되어있을 수 있기 때문에, 과거 부분이 부족한지를 검사한다.
        int savedCount = backfillHistoricalPeriodPricesIfNeeded(
                stock, interval, historyStartDate, latest.get());
		// DB에 저장되어있는 가장 최신일자
        LocalDate latestCandleDate = latest.get().getCandleDate();

        if (!interval.periodEnd(latestCandleDate).isBefore(completedPeriodEnd)) {
            return savedCount;
        }
		// DB에 저장된 최신시점 ~ DB에 저장되어 있어야 하는 최신기간의 데이터를 온디멘드 방식으로 요청해서 저장한다.
        return savedCount + stockPeriodPriceSyncService.fetchAndSavePeriodPrices(
                stock.getId(),
                interval,
                interval.nextPeriodStart(latestCandleDate),
                completedPeriodEnd,
                DEFAULT_ADJUSTED_PRICE);
    }
	// 만약 2020~2026 데이터가 필요한데, DB에는 2024~2026데이터만 저장되어있을 수 있기 때문에, 과거 부분이 부족한지를 검사한다.
    private int backfillHistoricalPeriodPricesIfNeeded(Stock stock,
                                                       StockChartInterval interval,
                                                       LocalDate historyStartDate,
                                                       StockPeriodPrice latestPrice) {
		// DB에 저장되어 있는 데이터중 가장 오래된 데이터를 조회한다.
        StockPeriodPrice oldestPrice = stockPeriodPriceRepository
                .findTopByStock_IdAndIntervalAndAdjustedPriceOrderByCandleDateAsc(
                        stock.getId(), interval, DEFAULT_ADJUSTED_PRICE)
                .orElse(latestPrice);
		// DB에 저장되어 있어야 하는 첫 기준일을 등록한다.
        LocalDate requestedPeriodStart = interval.periodStart(historyStartDate);
		// DB에 저장되어 있는 데이터의 기준일을 조회한다.
        LocalDate oldestPeriodStart = interval.periodStart(oldestPrice.getCandleDate());
        if (!oldestPeriodStart.isAfter(requestedPeriodStart)) {
            return 0;
        }
		// DB에 저장되어있어야 하는 첫 기준일 ~ DB에 저장되어 있는 데이터의 기준일 -1 사이의 데이터를 조회하여 동기화한다.
        return stockPeriodPriceSyncService.fetchAndSavePeriodPrices(
                stock.getId(),
                interval,
                historyStartDate,
                oldestPeriodStart.minusDays(1),
                DEFAULT_ADJUSTED_PRICE);
    }

	// 프론트에 전달할 기간봉 리스트를 만든다.
    private PeriodChartLoadResult loadPeriodCandles(Stock stock,
                                                    StockChartInterval interval,
                                                    LocalDate chartStartDate,
                                                    LocalDate completedPeriodEnd,
                                                    LocalDate expectedLatestTradeDate,
                                                    LocalDate marketDate) {
		// DB에서 기간봉 데이터(확정된 기간봉 데이터)를 조회한다.
        List<StockChartCandleData> candles = new ArrayList<>(stockPeriodPriceRepository
                .findByStock_IdAndIntervalAndAdjustedPriceAndCandleDateBetweenOrderByCandleDateAsc(
                        stock.getId(), interval, DEFAULT_ADJUSTED_PRICE, chartStartDate, completedPeriodEnd)
                .stream()
                .map(StockChartCandleData::from)
                .toList());
		// 아직 확정되지 않은 기간봉 데이터는 이에 해당하는 일봉데이터들을 통합해서 제공한다.
        LocalDate currentPeriodStart = interval.periodStart(marketDate);
        List<StockDailyPrice> currentPeriodDailyPrices = List.of();
        if (!expectedLatestTradeDate.isBefore(currentPeriodStart)) {
            currentPeriodDailyPrices = stockDailyPriceRepository
                    .findByStock_IdAndAdjustedPriceAndTradeDateBetweenOrderByTradeDateAsc(
                            stock.getId(), DEFAULT_ADJUSTED_PRICE, currentPeriodStart, expectedLatestTradeDate);
            StockChartCandleAggregator.aggregate(currentPeriodStart, currentPeriodDailyPrices)
                    .ifPresent(candles::add);
        }
        List<StockDailyPrice> baseDailyPrices = currentPeriodDailyPrices.stream()
                .filter(price -> !price.getTradeDate().equals(marketDate))
                .toList();
        StockChartCandleData baseCandle = StockChartCandleAggregator
                .aggregate(currentPeriodStart, baseDailyPrices)
                .orElse(null);
        return new PeriodChartLoadResult(
                List.copyOf(candles),
                baseCandle == null ? 0L : baseCandle.accumulatedVolume(),
                baseCandle == null
                        ? BigDecimal.ZERO
                        : baseCandle.accumulatedTradeAmount());
    }

	// 최신 일봉과 그 직전 일봉, 최대 2개를 반환하는 메서드
    private List<StockChartCandleData> loadLatestDailyCandles(Stock stock) {
        Optional<StockDailyPrice> latest = stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceOrderByTradeDateDesc(
                        stock.getId(), DEFAULT_ADJUSTED_PRICE);
        if (latest.isEmpty()) {
            return List.of();
        }
        List<StockChartCandleData> candles = new ArrayList<>();
        stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        stock.getId(), DEFAULT_ADJUSTED_PRICE, latest.get().getTradeDate().minusDays(1))
                .map(StockChartCandleData::from)
                .ifPresent(candles::add);
        candles.add(StockChartCandleData.from(latest.get()));
        return List.copyOf(candles);
    }

	// Interval 별로 차트를 어느시점부터 보여줄것인지를 결정한다.
    private LocalDate resolveIntervalHistoryStartDate(Stock stock,
                                                      StockChartInterval interval,
                                                      LocalDate marketDate) {
        LocalDate requestedStart = switch (interval) {
            case MINUTE_1, MINUTE_3, MINUTE_5, MINUTE_15 -> marketDate;
            case DAY -> marketDate.minusYears(INITIAL_HISTORY_YEARS); // 일봉 데이터의 경우 3년치 일봉 데이터를 제공
            case WEEK -> marketDate.minusYears(WEEK_HISTORY_YEARS); // 주봉 데이터의 경우에는 10년치 주봉 데이터를 제공한다.
            case MONTH, YEAR -> stock.getListedDate() == null // 월봉과 연봉은 상장일을 알면 해당 상장일부터, 모르면 1900년부터 모두 조회한다.
                    ? UNKNOWN_LISTED_DATE_FALLBACK
                    : stock.getListedDate();
        };
        return interval.periodStart(resolveHistoryStartDate(stock, requestedStart));
    }

    private LocalDate candleDate(List<StockChartCandleData> candles, int index, LocalDate fallback) {
        if (candles == null || candles.isEmpty() || index < 0 || index >= candles.size()) {
            return fallback;
        }
        String value = candles.get(index).tradeDate();
        return value == null || value.length() < 10 ? fallback : LocalDate.parse(value.substring(0, 10));
    }

    // 종목 상장일과 3년점 시점중 더 최근 시점을 리턴한다. (기준일자)
    private LocalDate resolveHistoryStartDate(Stock stock, LocalDate initialFetchStartDate) {
        LocalDate listedDate = stock.getListedDate();
        if (listedDate == null || listedDate.isBefore(initialFetchStartDate)) {
            return initialFetchStartDate;
        }
        return listedDate;
    }

    private StockMetadataDisplayInfo getMetadataDisplayInfo(Stock stock) {
        DomesticStockMetadata domesticMetadata = domesticStockMetadataRepository
                .findByStock_Id(stock.getId())
                .orElse(null);
        if (domesticMetadata != null) {
            Map<String, String> domesticSectorNames = stockIndustryCodeService.findDomesticSectorNames(Arrays.asList(
                    domesticMetadata.getSectorLargeDivisionCode(),
                    domesticMetadata.getSectorMediumDivisionCode(),
                    domesticMetadata.getSectorSmallDivisionCode()));
            StockIndustryDisplayNames industryDisplayNames =
                    new StockIndustryDisplayNames(domesticSectorNames, null);
            return StockMetadataDisplayInfo.from(stock, domesticMetadata, null, industryDisplayNames);
        }

        OverseasStockMetadata overseasMetadata = overseasStockMetadataRepository
                .findByStock_Id(stock.getId())
                .orElse(null);
        if (overseasMetadata == null) {
            return StockMetadataDisplayInfo.from(stock, null, null);
        }

        String overseasIndustryName = stockIndustryCodeService
                .resolveOverseasIndustryName(overseasMetadata.getExchangeCode(), overseasMetadata.getIndustryCode())
                .orElse(null);
        StockIndustryDisplayNames industryDisplayNames =
                new StockIndustryDisplayNames(Map.of(), overseasIndustryName);
        return StockMetadataDisplayInfo.from(stock, null, overseasMetadata, industryDisplayNames);
    }

	// 캔들 + 거래량 + 거래대금을 담은 레코드
    private record PeriodChartLoadResult(
            List<StockChartCandleData> candles,
            Long realtimeBaseVolume,
            BigDecimal realtimeBaseTradeAmount
    ) {
    }

}
