package com.finmate.repository.stock.metadata;

import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OverseasStockMetadataRepository extends JpaRepository<OverseasStockMetadata, Long> {
    Optional<OverseasStockMetadata> findByStock_Id(Long stockId);
}
