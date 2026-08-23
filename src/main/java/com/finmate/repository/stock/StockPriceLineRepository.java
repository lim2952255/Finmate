package com.finmate.repository.stock;

import com.finmate.domain.stock.StockPriceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockPriceLineRepository extends JpaRepository<StockPriceLine, Long> {
    List<StockPriceLine> findAllByUser_IdAndStock_IdOrderByCreatedAtAsc(Long userId, Long stockId);

    Optional<StockPriceLine> findByUser_IdAndStock_IdAndPrice(Long userId, Long stockId, BigDecimal price);

    Optional<StockPriceLine> findByIdAndUser_IdAndStock_Id(Long id, Long userId, Long stockId);

    void deleteAllByUser_IdAndStock_Id(Long userId, Long stockId);
}
