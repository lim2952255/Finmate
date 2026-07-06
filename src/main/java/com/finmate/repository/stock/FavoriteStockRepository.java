package com.finmate.repository.stock;

import com.finmate.domain.stock.FavoriteStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteStockRepository extends JpaRepository<FavoriteStock, Long> {
    Optional<FavoriteStock> findByUser_IdAndStock_Id(Long userId, Long stockId);

    @Query("""
            select f.stock.id
            from FavoriteStock f
            where f.user.id = :userId
    """)
    List<Long> findStockIdsByUserId(@Param("userId") Long userId);

    @Query(value = """
            select f
            from FavoriteStock f
            join fetch f.stock s
            where f.user.id = :userId
            order by f.createdAt desc
            """,
            countQuery = """
            select count(f)
            from FavoriteStock f
            where f.user.id = :userId
            """)
    Page<FavoriteStock> findPageByUserId(@Param("userId") Long userId, Pageable pageable);
}
