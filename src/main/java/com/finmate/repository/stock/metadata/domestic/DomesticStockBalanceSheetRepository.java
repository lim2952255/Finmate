package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticFinancialPeriodType;
import com.finmate.domain.stock.metadata.domestic.DomesticStockBalanceSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DomesticStockBalanceSheetRepository extends JpaRepository<DomesticStockBalanceSheet, Long> {
    Optional<DomesticStockBalanceSheet> findByStock_IdAndPeriodTypeAndFiscalPeriod(
            Long stockId, DomesticFinancialPeriodType periodType, LocalDate fiscalPeriod);

    Optional<DomesticStockBalanceSheet> findTopByStock_IdAndPeriodTypeOrderByUpdatedAtDesc(
            Long stockId, DomesticFinancialPeriodType periodType);

    boolean existsByStock_IdAndPeriodType(Long stockId, DomesticFinancialPeriodType periodType);

    List<DomesticStockBalanceSheet> findTop4ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
            Long stockId, DomesticFinancialPeriodType periodType);
}
