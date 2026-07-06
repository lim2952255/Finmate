package com.finmate.repository.stock.price;

import com.finmate.domain.stock.price.StockDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockDailyPriceRepository extends JpaRepository<StockDailyPrice, Long> {
    boolean existsByStock_IdAndTradeDateAndAdjustedPrice(Long stockId,
                                                         LocalDate tradeDate,
                                                         boolean adjustedPrice);

    Optional<StockDailyPrice> findTopByStock_IdAndAdjustedPriceOrderByTradeDateDesc(Long stockId,
                                                                                    boolean adjustedPrice);

    List<StockDailyPrice> findByStock_IdAndAdjustedPriceAndTradeDateBetweenOrderByTradeDateAsc(Long stockId,
                                                                                                boolean adjustedPrice,
                                                                                                LocalDate startDate,
                                                                                                LocalDate endDate);
}
