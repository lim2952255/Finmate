package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.StockChartPeriod;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo;
import com.finmate.domain.stock.dto.detail.StockDetailPageInfo;
import com.finmate.domain.stock.dto.detail.StockIndustryDisplayNames;
import com.finmate.domain.stock.dto.detail.StockMetadataDisplayInfo;
import com.finmate.domain.stock.metadata.domestic.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.overseas.OverseasStockMetadata;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockDetailService {
    private static final int INITIAL_HISTORY_YEARS = 3; // 기본 조회 기간
    private static final int HISTORY_START_TOLERANCE_DAYS = 7; // 주말·휴장일을 포함한 최초 거래일 허용 범위
    private static final boolean DEFAULT_ADJUSTED_PRICE = true; // 수정 주가

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockDailyPriceSyncService stockDailyPriceSyncService;
    private final StockIndustryCodeService stockIndustryCodeService;
    private final DomesticStockMetadataRepository domesticStockMetadataRepository;
    private final OverseasStockMetadataRepository overseasStockMetadataRepository;
    private final DomesticStockDetailRefreshService domesticStockDetailRefreshService;
    private final DomesticStockDetailQueryService domesticStockDetailQueryService;

    // 특정 기간동안의 특정 종목의 일봉 데이터들을 조회
    public StockDetailPageInfo getStockDetailPageInfo(Long stockId, StockChartPeriod period) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));

        // 차트 조회기간을 설정하지 않으면 기본값으로 1년으로 설정
        StockChartPeriod selectedPeriod = period == null ? StockChartPeriod.ONE_YEAR : period;
        // 현재 시간과 시장 타입을 기준으로 예상되는 최신 거래일 계산(저장되어 있어야 하는 최신 날짜)
        LocalDate expectedLatestTradeDate = StockMarketSchedules.expectedLatestDailyPriceTradeDate(stock.getMarketType());
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
        DomesticStockDetailInfo domesticDetailInfo = getDomesticDetailInfo(stock);

        // StockDetailPageInfo라는 DTO에 dailyPrices 리스트를 담아서 리턴한다.
        return new StockDetailPageInfo(
                stock,
                selectedPeriod,
                chartStartDate,
                expectedLatestTradeDate,
                savedDailyPriceCount,
                dailyPrices,
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
    private int syncIfNeeded(Stock stock,
                             LocalDate initialFetchStartDate,
                             LocalDate expectedLatestTradeDate) {
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

}
