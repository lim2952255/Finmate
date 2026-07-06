package com.finmate.repository.stock.metadata;

import com.finmate.domain.stock.metadata.DomesticStockMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomesticStockMetadataRepository extends JpaRepository<DomesticStockMetadata, Long> {
    Optional<DomesticStockMetadata> findByStock_Id(Long stockId);
}
