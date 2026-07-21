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

    @Query("""
            select s
            from Stock s
            where s.realtimeSymbol = :realtimeKey
               or s.symbol = :realtimeKey
               or concat('DNAS', s.symbol) = :realtimeKey
            """)
    Optional<Stock> findByRealtimeKey(@Param("realtimeKey") String realtimeKey);

    // favoriteStock과 left join을 수행하여 관심종목을 우선적으로 정렬하여 조회
    //coalesce: null 대체 함수
    @Query(value = """
            select s
            from Stock s
            left join FavoriteStock f
              on f.stock = s
             and f.user.id = :userId
            where s.active = true
              and (:marketType is null or s.marketType = :marketType)
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
              and (:marketType is null or s.marketType = :marketType)
              and (
                  lower(s.symbol) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.standardCode, '')) like lower(concat('%', :keyword, '%'))
                  or lower(s.nameKo) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.nameEn, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Stock> searchByKeywordWithFavoriteFirst(@Param("userId") Long userId,
                                                 @Param("keyword") String keyword,
                                                 @Param("marketType") StockMarketType marketType,
                                                 Pageable pageable);

    default Page<Stock> searchByKeywordWithFavoriteFirst(Long userId, String keyword, Pageable pageable) {
        return searchByKeywordWithFavoriteFirst(userId, keyword, null, pageable);
    }

    // 국내/해외 업종명 또는 업종코드를 기준으로 종목을 검색한다.
    // 국내는 대/중/소 업종코드 전체를 대상으로 하고, 해외는 거래소별 업종코드명을 대상으로 한다.
    @Query(value = """
            select s
            from Stock s
            left join FavoriteStock f
              on f.stock = s
             and f.user.id = :userId
            where s.active = true
              and (:marketType is null or s.marketType = :marketType)
              and (
                  exists (
                      select 1
                      from DomesticStockMetadata m
                      where m.stock = s
                        and (
                            lower(coalesce(m.sectorLargeDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.sectorMediumDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.sectorSmallDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or m.sectorLargeDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                            or m.sectorMediumDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                            or m.sectorSmallDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                        )
                  )
                  or exists (
                      select 1
                      from OverseasStockMetadata m
                      where m.stock = s
                        and (
                            lower(coalesce(m.industryCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.exchangeCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.exchangeName, '')) like lower(concat('%', :keyword, '%'))
                            or m.industryCode in (
                                select c.industryCode
                                from OverseasStockIndustryCode c
                                where c.exchangeCode = m.exchangeCode
                                  and (
                                      lower(c.industryCode) like lower(concat('%', :keyword, '%'))
                                      or lower(c.name) like lower(concat('%', :keyword, '%'))
                                  )
                            )
                        )
                  )
              )
            order by
              case when f.id is not null then 0 else 1 end,
              s.symbol asc
            """,
            countQuery = """
            select count(s)
            from Stock s
            where s.active = true
              and (:marketType is null or s.marketType = :marketType)
              and (
                  exists (
                      select 1
                      from DomesticStockMetadata m
                      where m.stock = s
                        and (
                            lower(coalesce(m.sectorLargeDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.sectorMediumDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.sectorSmallDivisionCode, '')) like lower(concat('%', :keyword, '%'))
                            or m.sectorLargeDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                            or m.sectorMediumDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                            or m.sectorSmallDivisionCode in (
                                select c.code
                                from DomesticStockSectorCode c
                                where lower(c.code) like lower(concat('%', :keyword, '%'))
                                   or lower(c.nameKo) like lower(concat('%', :keyword, '%'))
                            )
                        )
                  )
                  or exists (
                      select 1
                      from OverseasStockMetadata m
                      where m.stock = s
                        and (
                            lower(coalesce(m.industryCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.exchangeCode, '')) like lower(concat('%', :keyword, '%'))
                            or lower(coalesce(m.exchangeName, '')) like lower(concat('%', :keyword, '%'))
                            or m.industryCode in (
                                select c.industryCode
                                from OverseasStockIndustryCode c
                                where c.exchangeCode = m.exchangeCode
                                  and (
                                      lower(c.industryCode) like lower(concat('%', :keyword, '%'))
                                      or lower(c.name) like lower(concat('%', :keyword, '%'))
                                  )
                            )
                        )
                  )
              )
            """)
    Page<Stock> searchByIndustryKeywordWithFavoriteFirst(@Param("userId") Long userId,
                                                         @Param("keyword") String keyword,
                                                         @Param("marketType") StockMarketType marketType,
                                                         Pageable pageable);

    default Page<Stock> searchByIndustryKeywordWithFavoriteFirst(Long userId, String keyword, Pageable pageable) {
        return searchByIndustryKeywordWithFavoriteFirst(userId, keyword, null, pageable);
    }
}
