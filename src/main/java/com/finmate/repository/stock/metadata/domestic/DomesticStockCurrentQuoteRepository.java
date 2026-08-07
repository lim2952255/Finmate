package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockCurrentQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DomesticStockCurrentQuoteRepository extends JpaRepository<DomesticStockCurrentQuote, Long> {
    Optional<DomesticStockCurrentQuote> findByStock_Id(Long stockId);
}
