package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticFinancialPeriodType;
import com.finmate.domain.stock.metadata.domestic.DomesticStockIncomeStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DomesticStockIncomeStatementRepository extends JpaRepository<DomesticStockIncomeStatement, Long> {
    Optional<DomesticStockIncomeStatement> findByStock_IdAndPeriodTypeAndFiscalPeriod(
            Long stockId, DomesticFinancialPeriodType periodType, LocalDate fiscalPeriod);

    Optional<DomesticStockIncomeStatement> findTopByStock_IdAndPeriodTypeOrderByUpdatedAtDesc(
            Long stockId, DomesticFinancialPeriodType periodType);

    boolean existsByStock_IdAndPeriodType(Long stockId, DomesticFinancialPeriodType periodType);

    List<DomesticStockIncomeStatement> findTop6ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
            Long stockId, DomesticFinancialPeriodType periodType);
}
