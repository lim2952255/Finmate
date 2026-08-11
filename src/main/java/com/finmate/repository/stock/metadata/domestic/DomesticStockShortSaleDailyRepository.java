package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockShortSaleDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomesticStockShortSaleDailyRepository extends JpaRepository<DomesticStockShortSaleDaily, Long> {
    Optional<DomesticStockShortSaleDaily> findTopByStock_IdOrderByTradeDateDesc(Long stockId);
    boolean existsByStock_Id(Long stockId);
    List<DomesticStockShortSaleDaily> findTop70ByStock_IdOrderByTradeDateDesc(Long stockId);

}
