package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockInvestorDailyTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomesticStockInvestorDailyTradeRepository extends JpaRepository<DomesticStockInvestorDailyTrade, Long> {
    Optional<DomesticStockInvestorDailyTrade> findTopByStock_IdAndMarketCodeOrderByUpdatedAtDesc(
            Long stockId, String marketCode);

    Optional<DomesticStockInvestorDailyTrade> findTopByStock_IdAndMarketCodeOrderByTradeDateDesc(
            Long stockId, String marketCode);

    List<DomesticStockInvestorDailyTrade> findTop20ByStock_IdAndMarketCodeOrderByTradeDateDesc(
            Long stockId, String marketCode);
}
