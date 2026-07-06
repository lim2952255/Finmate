package com.finmate.repository.normal.transfer;

import com.finmate.domain.normal.transfer.DailyTransferUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

// 일일 이체한도 조회용
@Repository
public interface DailyTransferUsageRepository extends JpaRepository<DailyTransferUsage, Long> {

    Optional<DailyTransferUsage> findByAccount_IdAndUsageDate(Long accountId, LocalDate usageDate);

    // 일일 이체한도 확인도 동시성 문제가 발생할 수 있기 때문에 비관적 락을 걸어줘야 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from DailyTransferUsage u
            where u.account.id = :accountId
              and u.usageDate = :usageDate
            """)
    Optional<DailyTransferUsage> findByAccountIdAndUsageDateForUpdate(@Param("accountId") Long accountId,
                                                                      @Param("usageDate") LocalDate usageDate);
}
