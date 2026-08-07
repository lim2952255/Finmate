package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockInvestorDailyTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DomesticStockInvestorDailyTradeRepository extends JpaRepository<DomesticStockInvestorDailyTrade, Long> {
    Optional<DomesticStockInvestorDailyTrade> findByStock_IdAndMarketCodeAndTradeDate(
            Long stockId, String marketCode, LocalDate tradeDate);

    Optional<DomesticStockInvestorDailyTrade> findTopByStock_IdAndMarketCodeOrderByUpdatedAtDesc(
            Long stockId, String marketCode);

    List<DomesticStockInvestorDailyTrade> findTop20ByStock_IdAndMarketCodeOrderByTradeDateDesc(
            Long stockId, String marketCode);
}
