package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticStockLoanTransactionDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DomesticStockLoanTransactionDailyRepository extends JpaRepository<DomesticStockLoanTransactionDaily, Long> {
    Optional<DomesticStockLoanTransactionDaily> findByStock_IdAndTradeDate(Long stockId, LocalDate tradeDate);
    Optional<DomesticStockLoanTransactionDaily> findTopByStock_IdOrderByTradeDateDesc(Long stockId);
    boolean existsByStock_Id(Long stockId);
    List<DomesticStockLoanTransactionDaily> findTop70ByStock_IdOrderByTradeDateDesc(Long stockId);

}
