package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.detail.DomesticStockDailyFlowSnapshot;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.metadata.domestic.DomesticStockDetailRefreshState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 투자자 수급·공매도·대차의 데이터 수명주기를 관리한다.
 * 마감된 거래일은 DB에 동기화하고, 장중 금일 데이터는 Redis에만 보관한다.
 */
@Service
public class DomesticStockDailyFlowRefreshService {
    private final DomesticStockFinalizedDailyFlowSyncService finalizedSyncService; // 마감된 거래일의 데이터를 DB에 동기화하는 서비스
    private final DomesticStockIntradayDailyFlowService intradayService; // 장중 금일 데이터를 Redis 캐싱용 DTO로 변환하는 서비스
    private final DomesticStockDailyFlowCacheService cacheService; // 장중 금일 데이터를 Redis에 캐싱하는 서비스
    private final Duration intradayCacheTtl;

    public DomesticStockDailyFlowRefreshService(
            DomesticStockFinalizedDailyFlowSyncService finalizedSyncService,
            DomesticStockIntradayDailyFlowService intradayService,
            DomesticStockDailyFlowCacheService cacheService,
            @Value("${finmate.stock-detail.investor-refresh-seconds:600}") long investorRefreshSeconds,
            @Value("${finmate.stock-detail.short-loan-refresh-seconds:600}") long shortLoanRefreshSeconds) {
        this.finalizedSyncService = finalizedSyncService;
        this.intradayService = intradayService;
        this.cacheService = cacheService;
        this.intradayCacheTtl = Duration.ofSeconds(
                Math.min(investorRefreshSeconds, shortLoanRefreshSeconds));
    }

    // DB 갱신 및 Redis 캐싱 수행
    public void refreshIfNeeded(Stock stock, DomesticStockDetailRefreshState state) {
        LocalDate finalizedDate = StockMarketSchedules // DB에 저장되어 있어야 하는 최신 거래일
                .expectedLatestDailyPriceTradeDate(stock.getMarketType());
        LocalDate currentDataDate = StockMarketSchedules // 현재 조회 가능한 가장 최신 장중 데이터 날짜
                .expectedLatestRegularMarketDataTradeDate(stock.getMarketType());
        // 만약 8월 11일 장중이라면 finalizedDate: 8월 10일, currentDataDate: 8월 11일이 된다.
        refresh(stock, state, finalizedDate, currentDataDate);
    }

    void refresh(Stock stock,
                 DomesticStockDetailRefreshState state,
                 LocalDate finalizedDate,
                 LocalDate currentDataDate) {
        // 부족한 데이터를 온디멘드방식으로 조회해서 DB에 저장한다.
        finalizedSyncService.synchronize(stock, state, finalizedDate);

        // currentDataDate == finalizedDate라면 이는 장 마감시간대라는 의미이기 때문에 DB에서 데이터를 조회하면 된다. 따라서 Redis에 캐싱되어 있는 데이터를 제거한다.
        if (!currentDataDate.isAfter(finalizedDate)) {
            cacheService.evict(stock.getSymbol());
            return;
        }

        // currentDataDate > finalizedDate라면 이는 장 중 시간대라는 의미이기 때문에 Redis에서 데이터를 조회하고, 데이터가 없다면 KIS API를 호출해서 데이터 조회후 Redis에 캐싱한다.
        boolean hasCurrentSnapshot = cacheService.get(stock.getSymbol())
                .map(DomesticStockDailyFlowSnapshot::tradeDate)
                .filter(currentDataDate::equals)
                .isPresent();
        if (hasCurrentSnapshot) {
            return;
        }

        DomesticStockDailyFlowSnapshot snapshot = intradayService.fetch(stock, currentDataDate);
        cacheService.put(stock.getSymbol(), snapshot, intradayCacheTtl);
    }
}
