package com.finmate.repository.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findByMarketType(StockMarketType marketType);

    Optional<Stock> findByMarketTypeAndSymbol(StockMarketType marketType, String symbol);

    // favoriteStock과 left join을 수행하여 관심종목을 우선적으로 정렬하여 조회
    //coalesce: null 대체 함수
    @Query(value = """
            select s
            from Stock s
            left join FavoriteStock f
              on f.stock = s
             and f.user.id = :userId
            where s.active = true
              and (
                  lower(s.symbol) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.standardCode, '')) like lower(concat('%', :keyword, '%'))
                  or lower(s.nameKo) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.nameEn, '')) like lower(concat('%', :keyword, '%'))
              )
            order by
              case when f.id is not null then 0 else 1 end,
              s.symbol asc
            """,
            countQuery = """
            select count(s)
            from Stock s
            where s.active = true
              and (
                  lower(s.symbol) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.standardCode, '')) like lower(concat('%', :keyword, '%'))
                  or lower(s.nameKo) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.nameEn, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Stock> searchByKeywordWithFavoriteFirst(@Param("userId") Long userId,
                                                 @Param("keyword") String keyword,
                                                 Pageable pageable);
}
