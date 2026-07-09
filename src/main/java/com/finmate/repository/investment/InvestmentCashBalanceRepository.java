package com.finmate.repository.investment;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentCashBalanceRepository extends JpaRepository<InvestmentCashBalance, Long> {

    List<InvestmentCashBalance> findByInvestmentAccount_Id(Long investmentId);

    // 특정 증권 계좌가 보유중인 잔고에서 lock을 걸거나, lock을 해제할 때, 동시성과 정합성을 보장하기 위해 비관적 락을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from InvestmentCashBalance b
            where b.investmentAccount.id = :investmentId
              and b.currencyCode = :currencyCode
            """)
    Optional<InvestmentCashBalance> findByInvestmentIdAndCurrencyCodeForUpdate(
            @Param("investmentId") Long investmentId,
            @Param("currencyCode") CurrencyCode currencyCode);
}
