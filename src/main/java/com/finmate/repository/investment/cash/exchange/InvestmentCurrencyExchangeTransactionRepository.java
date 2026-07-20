package com.finmate.repository.investment.cash.exchange;

import com.finmate.domain.investment.cash.exchange.InvestmentCurrencyExchangeTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

// 환전 내역 저장 Repository
@Repository
public interface InvestmentCurrencyExchangeTransactionRepository extends JpaRepository<InvestmentCurrencyExchangeTransaction, Long> {

    // 특정 사용자의 모든 증권계좌 통합 환전 내역 출력
    // 이때 환전 내역의 수가 많을 수 있기때문에 페이징을 사용한다.
    @Query(value = """
            select t
            from InvestmentCurrencyExchangeTransaction t
            join fetch t.investment i
            join fetch i.user u
            where i.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from InvestmentCurrencyExchangeTransaction t
            where t.investment.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    // @Query를 통해 직접 쿼리를 작성한 경우에도, 반환타입이 Page<T>이고, Pageable 파라미터가 있으면 스프링 데이터 JPA가 페이징 처리를 해준다.
    Page<InvestmentCurrencyExchangeTransaction> findAllByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable);

    // 특정 사용자의 특정 계좌 환전 내역 출력
    @Query(value = """
            select t
            from InvestmentCurrencyExchangeTransaction t
            join fetch t.investment i
            join fetch i.user u
            where i.id = :investmentId
              and i.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from InvestmentCurrencyExchangeTransaction t
            where t.investment.id = :investmentId
              and t.investment.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    Page<InvestmentCurrencyExchangeTransaction> findAllByUserIdAndInvestmentIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("investmentId") Long investmentId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable);
}
