package com.finmate.repository.investment.cash.transaction;

import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransaction;
import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 증권계좌로의 예수금 입출력 내역 출력용
@Repository
public interface SecuritiesCashTransactionRepository extends JpaRepository<SecuritiesCashTransaction, Long> {

    @Query(value = """
            select t
            from SecuritiesCashTransaction t
            join fetch t.investment i
            join fetch i.user u
            where i.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from SecuritiesCashTransaction t
            where t.investment.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    Page<SecuritiesCashTransaction> findAllByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                                       @Param("startDateTime") LocalDateTime startDateTime,
                                                                       @Param("endDateTime") LocalDateTime endDateTime,
                                                                       Pageable pageable);

    @Query(value = """
            select t
            from SecuritiesCashTransaction t
            join fetch t.investment i
            join fetch i.user u
            where i.id = :investmentId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from SecuritiesCashTransaction t
            where t.investment.id = :investmentId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    Page<SecuritiesCashTransaction> findAllByInvestmentIdAndCreatedAtBetween(@Param("investmentId") Long investmentId,
                                                                             @Param("startDateTime") LocalDateTime startDateTime,
                                                                             @Param("endDateTime") LocalDateTime endDateTime,
                                                                             Pageable pageable);
    // 특정 사용자의 모든 증권 계좌의 예수금 입출금 합계
    @Query("""
            select sum(t.amount)
            from SecuritiesCashTransaction t
            where t.investment.user.id = :userId
              and t.type in :types
              and t.createdAt between :startDateTime and :endDateTime
            """)
    BigDecimal sumAmountByUserIdAndTypes(@Param("userId") Long userId,
                                         @Param("types") List<SecuritiesCashTransactionType> types,
                                         @Param("startDateTime") LocalDateTime startDateTime,
                                         @Param("endDateTime") LocalDateTime endDateTime);

    // 특정 계좌 하나의 예수금 입출금 합계
    @Query("""
            select sum(t.amount)
            from SecuritiesCashTransaction t
            where t.investment.id = :investmentId
              and t.type in :types
              and t.createdAt between :startDateTime and :endDateTime
            """)
    BigDecimal sumAmountByInvestmentIdAndTypes(@Param("investmentId") Long investmentId,
                                               @Param("types") List<SecuritiesCashTransactionType> types,
                                               @Param("startDateTime") LocalDateTime startDateTime,
                                               @Param("endDateTime") LocalDateTime endDateTime);
}
