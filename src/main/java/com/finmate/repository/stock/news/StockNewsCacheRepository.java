package com.finmate.repository.stock.news;

import com.finmate.domain.stock.news.StockNewsCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockNewsCacheRepository extends JpaRepository<StockNewsCache, Long> {
    Optional<StockNewsCache> findByStock_Id(Long stockId);
}
