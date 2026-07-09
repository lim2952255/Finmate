package com.finmate.repository.stock.trading;

import com.finmate.domain.stock.trading.StockHolding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockHoldingRepository extends JpaRepository<StockHolding, Long> {

    List<StockHolding> findByInvestment_Id(Long investmentId);

    Optional<StockHolding> findByInvestment_IdAndStock_Id(Long investmentId, Long stockId);

    @Query("""
            select h
            from StockHolding h
            join fetch h.stock
            join fetch h.investment i
            where i.id = :investmentId
              and h.quantity > 0
            order by h.stock.symbol asc
            """)
    List<StockHolding> findPortfolioHoldingsByInvestmentId(@Param("investmentId") Long investmentId);

    @Query("""
            select h
            from StockHolding h
            join fetch h.stock
            join fetch h.investment i
            where i.user.id = :userId
              and h.quantity > 0
            order by h.stock.symbol asc, i.accountNumber asc
            """)
    List<StockHolding> findPortfolioHoldingsByUserId(@Param("userId") Long userId);

    @Query("""
            select h
            from StockHolding h
            join fetch h.investment i
            where i.user.id = :userId
              and h.stock.id = :stockId
            """)
    List<StockHolding> findOrderHoldingsByUserIdAndStockId(@Param("userId") Long userId,
                                                           @Param("stockId") Long stockId);

    // 특정 증권계좌가 보유중인 종목의 lock 상태를 update하기 위해, 동시성과 정합성을 위해 비관적 락을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h
            from StockHolding h
            where h.investment.id = :investmentId
              and h.stock.id = :stockId
            """)
    Optional<StockHolding> findByInvestmentIdAndStockIdForUpdate(@Param("investmentId") Long investmentId,
                                                                 @Param("stockId") Long stockId);
}
