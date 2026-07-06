package com.finmate.repository.investment;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    long countByUser_Id(Long userId);

    List<Investment> findByUser_Id(Long userId);

    Optional<Investment> findByUser_IdAndAccountNumberAndSecuritiesCompanyCode(
            Long userId,
            String accountNumber,
            SecuritiesCompanyCode securitiesCompanyCode);

    Optional<Investment> findByUser_IdAndPrimaryTrue(Long userId);

    // 예수금 입출금을 위해 비관적 락 획득
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Investment i where i.id = :id")
    Optional<Investment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Investment i where i.user.id = :userId order by i.id")
    List<Investment> findByUserIdForUpdate(@Param("userId") Long userId);
}
