package com.finmate.repository.stock.disclosure;

import com.finmate.domain.stock.disclosure.StockDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

// 종목별 시황/공시 이력과 외부 식별자 중복 여부를 조회한다.
public interface StockDisclosureRepository extends JpaRepository<StockDisclosure, Long> {
    // 화면에는 작성시각이 최신인 항목 10개만 제공한다.
    List<StockDisclosure> findTop10ByStock_IdOrderByDisclosedAtDescIdDesc(Long stockId);

    // KIS 응답을 저장하기 전에 식별자 키정보만 조회하여 KIS API응답들 중 이미 DB에 저장되어 있는 시황/공시정보는 필터링한다.
    @Query("select disclosure.externalKey from StockDisclosure disclosure "
            + "where disclosure.stock.id = :stockId and disclosure.externalKey in :externalKeys")
    Set<String> findExistingExternalKeys(
            @Param("stockId") Long stockId,
            @Param("externalKeys") Collection<String> externalKeys);
}
