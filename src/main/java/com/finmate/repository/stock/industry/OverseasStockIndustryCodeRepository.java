package com.finmate.repository.stock.industry;

import com.finmate.domain.stock.industry.OverseasStockIndustryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OverseasStockIndustryCodeRepository extends JpaRepository<OverseasStockIndustryCode, Long> {
    Optional<OverseasStockIndustryCode> findByExchangeCodeAndIndustryCode(String exchangeCode, String industryCode);
}
