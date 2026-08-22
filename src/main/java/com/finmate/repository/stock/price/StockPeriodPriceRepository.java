package com.finmate.repository.stock.price;

import com.finmate.domain.stock.dto.detail.StockChartInterval;
import com.finmate.domain.stock.price.StockPeriodPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPeriodPriceRepository extends JpaRepository<StockPeriodPrice, Long> {
    boolean existsByStock_IdAndIntervalAndCandleDateAndAdjustedPrice(Long stockId,
                                                                     StockChartInterval interval,
                                                                     LocalDate candleDate,
                                                                     boolean adjustedPrice);

    Optional<StockPeriodPrice> findTopByStock_IdAndIntervalAndAdjustedPriceOrderByCandleDateDesc(
            Long stockId, StockChartInterval interval, boolean adjustedPrice);

    Optional<StockPeriodPrice> findTopByStock_IdAndIntervalAndAdjustedPriceOrderByCandleDateAsc(
            Long stockId, StockChartInterval interval, boolean adjustedPrice);

    List<StockPeriodPrice> findByStock_IdAndIntervalAndAdjustedPriceAndCandleDateBetweenOrderByCandleDateAsc(
            Long stockId, StockChartInterval interval, boolean adjustedPrice, LocalDate startDate, LocalDate endDate);
}
