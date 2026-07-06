package com.finmate.repository.normal.account.transaction;

import com.finmate.domain.normal.account.transaction.AccountTransaction;
import com.finmate.domain.normal.account.transaction.AccountTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 거래내역 조회용
@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    // 쿼리메서드를 통해서도 페이징 기능을 활용할 수는 있지만, account가 Lazy Loading이기 때문에 이후 계좌를 하나씩 조회하게 되면 N+1문제가 발생할 수 있음
    // 따라서 패치조인을 활용해서 N+1문제를 방지하기 위해 JPQL을 직접 작성한다
    @Query(value = """
            select t
            from AccountTransaction t
            join fetch t.account a
            join fetch a.user u
            where a.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from AccountTransaction t
            where t.account.user.id = :userId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    Page<AccountTransaction> findAllByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                                @Param("startDateTime") LocalDateTime startDateTime,
                                                                @Param("endDateTime") LocalDateTime endDateTime,
                                                                Pageable pageable);

    // 패치조인을 활용해서 N+1문제 방지
    @Query(value = """
            select t
            from AccountTransaction t
            join fetch t.account a
            join fetch a.user u
            where a.id = :accountId
              and t.createdAt between :startDateTime and :endDateTime
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.createdAt between :startDateTime and :endDateTime
            """)
    Page<AccountTransaction> findAllByAccountIdAndCreatedAtBetween(@Param("accountId") Long accountId,
                                                                   @Param("startDateTime") LocalDateTime startDateTime,
                                                                   @Param("endDateTime") LocalDateTime endDateTime,
                                                                   Pageable pageable);
    // 특정 사용자의 모든 일반 계좌 거래 합계
    @Query("""
            select sum(t.amount)
            from AccountTransaction t
            where t.account.user.id = :userId
              and t.type in :types
              and t.createdAt between :startDateTime and :endDateTime
            """)
    BigDecimal sumAmountByUserIdAndTypes(@Param("userId") Long userId,
                                         @Param("types") List<AccountTransactionType> types,
                                         @Param("startDateTime") LocalDateTime startDateTime,
                                         @Param("endDateTime") LocalDateTime endDateTime);

    // 특정 계좌 하나의 거래 합계
    @Query("""
            select sum(t.amount)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.type in :types
              and t.createdAt between :startDateTime and :endDateTime
            """)
    BigDecimal sumAmountByAccountIdAndTypes(@Param("accountId") Long accountId,
                                            @Param("types") List<AccountTransactionType> types,
                                            @Param("startDateTime") LocalDateTime startDateTime,
                                            @Param("endDateTime") LocalDateTime endDateTime);
}
