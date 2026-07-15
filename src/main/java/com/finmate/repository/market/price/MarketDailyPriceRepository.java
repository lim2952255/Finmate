package com.finmate.repository.market.price;

import com.finmate.domain.market.MarketIndicatorType;
import com.finmate.domain.market.price.MarketDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDailyPriceRepository extends JpaRepository<MarketDailyPrice, Long> {
    boolean existsByIndicatorTypeAndIndicatorSymbolAndTradeDate(MarketIndicatorType indicatorType,
                                                                String indicatorSymbol,
                                                                LocalDate tradeDate);

    Optional<MarketDailyPrice> findTopByIndicatorTypeAndIndicatorSymbolOrderByTradeDateDesc(
            MarketIndicatorType indicatorType,
            String indicatorSymbol);

    List<MarketDailyPrice> findByIndicatorTypeAndIndicatorSymbolAndTradeDateBetweenOrderByTradeDateAsc(
            MarketIndicatorType indicatorType,
            String indicatorSymbol,
            LocalDate startDate,
            LocalDate endDate);
}
