package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockDetailRefreshState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DomesticStockDetailRefreshStateRepository
        extends JpaRepository<DomesticStockDetailRefreshState, Long> {
    Optional<DomesticStockDetailRefreshState> findByStock_Id(Long stockId);
}
