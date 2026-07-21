package com.finmate.repository.stock.industry;

import com.finmate.domain.stock.industry.DomesticStockSectorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DomesticStockSectorCodeRepository extends JpaRepository<DomesticStockSectorCode, Long> {
    List<DomesticStockSectorCode> findByCodeIn(Collection<String> codes);
}
