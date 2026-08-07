package com.finmate.repository.stock.metadata.domestic;

import com.finmate.domain.stock.metadata.domestic.DomesticFinancialPeriodType;
import com.finmate.domain.stock.metadata.domestic.DomesticStockFinancialRatio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DomesticStockFinancialRatioRepository extends JpaRepository<DomesticStockFinancialRatio, Long> {
    Optional<DomesticStockFinancialRatio> findByStock_IdAndPeriodTypeAndFiscalPeriod(
            Long stockId, DomesticFinancialPeriodType periodType, LocalDate fiscalPeriod);

    Optional<DomesticStockFinancialRatio> findTopByStock_IdAndPeriodTypeOrderByUpdatedAtDesc(
            Long stockId, DomesticFinancialPeriodType periodType);

    boolean existsByStock_IdAndPeriodType(Long stockId, DomesticFinancialPeriodType periodType);

    List<DomesticStockFinancialRatio> findTop4ByStock_IdAndPeriodTypeOrderByFiscalPeriodDesc(
            Long stockId, DomesticFinancialPeriodType periodType);
}
