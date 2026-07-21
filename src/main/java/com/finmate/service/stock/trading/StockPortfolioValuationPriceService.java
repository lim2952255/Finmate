package com.finmate.service.stock.trading;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.trading.StockPortfolioPriceSnapshot;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.price.StockDailyPrice;
import com.finmate.repository.stock.price.StockDailyPriceRepository;
import com.finmate.service.stock.price.StockDailyPriceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

// 포트폴리오에서 수익률을 최근 종가 기준으로 계산하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPortfolioValuationPriceService {
    private static final boolean DEFAULT_ADJUSTED_PRICE = true; // 수정주가 사용
    private static final int LATEST_PRICE_LOOKBACK_DAYS = 14; // 최근 종가를 조회할때 한번에 조회하는 기간
    private static final String DAILY_CLOSE_SOURCE_NAME = "최근 종가";

    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockDailyPriceSyncService stockDailyPriceSyncService; // KIS API를 호출하여 종목의 일봉데이터를 받아서 DB에 동기화하는 서비스

    // 포트폴리오상의 여러 종목들 최근 종가를 구하는 메서드
    public Map<Long, StockPortfolioPriceSnapshot> resolveLatestDailyCloseSnapshots(Collection<Stock> stocks) {
        Map<Long, Stock> stocksById = new LinkedHashMap<>();
        (stocks == null ? List.<Stock>of() : stocks).stream()
                .filter(Objects::nonNull)
                .filter(stock -> stock.getId() != null)
                // putIfAbsent는 해당 key값이 없을때만 값을 삽입하며, 종목이 중복으로 저장되는 것을 방지한다.
                .forEach(stock -> stocksById.putIfAbsent(stock.getId(), stock));
        if (stocksById.isEmpty()) {
            return Map.of();
        }

        Map<Long, StockPortfolioPriceSnapshot> snapshotsByStockId = new LinkedHashMap<>();
        for (Stock stock : stocksById.values()) {
            // 각 종목별로 최근 종가 정보를 받아서 저장한다.
            resolveLatestDailyCloseSnapshot(stock)
                    .ifPresent(snapshot -> snapshotsByStockId.put(stock.getId(), snapshot));
        }

        return snapshotsByStockId;
    }

    // 각 종목마다 최근 종가 구하기
    private Optional<StockPortfolioPriceSnapshot> resolveLatestDailyCloseSnapshot(Stock stock) {
        // 시장에서 기대하는 최신 거래일을 계산한다.
        LocalDate expectedLatestTradeDate =
                StockMarketSchedules.expectedLatestDailyPriceTradeDate(stock.getMarketType());
        // DB에서 최신 거래일의 데이터가 존재하는지 확인하고, 존재한다면 DB에서 조회하고, 없다면 KIS API를 호출해서 데이터를 받는다.
        syncLatestDailyPriceIfNeeded(stock, expectedLatestTradeDate);

        // 최신 거래일을 기반으로 최신 일봉을 조회하고, StockPortfolioPriceSnapshot에 최신 거래일과 종가 데이터를 전달한다.
        return stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        stock.getId(),
                        DEFAULT_ADJUSTED_PRICE,
                        expectedLatestTradeDate)
                .map(dailyPrice -> new StockPortfolioPriceSnapshot(
                        dailyPrice.getClosePrice(),
                        dailyPrice.getTradeDate(),
                        DAILY_CLOSE_SOURCE_NAME));
    }

    // 최신 거래일의 일봉데이터가 DB에 존재하는지를 확인한다.
    private void syncLatestDailyPriceIfNeeded(Stock stock, LocalDate expectedLatestTradeDate) {
        Optional<StockDailyPrice> latestDailyPrice = stockDailyPriceRepository
                .findTopByStock_IdAndAdjustedPriceAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        stock.getId(),
                        DEFAULT_ADJUSTED_PRICE,
                        expectedLatestTradeDate);
        // 이미 DB에 데이터가 존재한다면 바로 return
        if (latestDailyPrice.isPresent()
                && !latestDailyPrice.get().getTradeDate().isBefore(expectedLatestTradeDate)) {
            return;
        }

        LocalDate lookbackStartDate = expectedLatestTradeDate.minusDays(LATEST_PRICE_LOOKBACK_DAYS);
        LocalDate fetchStartDate = latestDailyPrice
                .map(StockDailyPrice::getTradeDate)
                .map(tradeDate -> tradeDate.plusDays(1))
                .filter(startDate -> !startDate.isBefore(lookbackStartDate))
                .orElse(lookbackStartDate);
        if (fetchStartDate.isAfter(expectedLatestTradeDate)) {
            return;
        }

        try {
            // stockDailyPriceSyncService를 호출하여 최신 거래일의 일봉데이터를 받아서 저장한다.
            stockDailyPriceSyncService.fetchAndSaveDailyPrices(
                    stock.getId(),
                    fetchStartDate,
                    expectedLatestTradeDate,
                    DEFAULT_ADJUSTED_PRICE);
        } catch (Exception e) {
            log.warn("포트폴리오 평가용 일봉 동기화에 실패했습니다. stockId={}, symbol={}",
                    stock.getId(),
                    stock.getSymbol(),
                    e);
        }
    }
}
