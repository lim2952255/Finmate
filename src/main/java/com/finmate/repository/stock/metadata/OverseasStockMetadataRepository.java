package com.finmate.repository.stock.metadata;

import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OverseasStockMetadataRepository extends JpaRepository<OverseasStockMetadata, Long> {
    Optional<OverseasStockMetadata> findByStock_Id(Long stockId);

    @Query("""
            select m
            from OverseasStockMetadata m
            join fetch m.stock
            where m.stock.id in :stockIds
            """)
    List<OverseasStockMetadata> findByStockIdsWithStock(@Param("stockIds") Collection<Long> stockIds);
}
