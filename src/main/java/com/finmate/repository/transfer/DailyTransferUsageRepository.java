package com.finmate.repository.transfer;

import com.finmate.domain.transfer.DailyTransferUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyTransferUsageRepository extends JpaRepository<DailyTransferUsage, Long> {

    Optional<DailyTransferUsage> findByAccount_IdAndUsageDate(Long accountId, LocalDate usageDate);

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
