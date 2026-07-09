package com.finmate.repository.stock.trading;

import com.finmate.domain.stock.trading.StockTradeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTradeTransactionRepository extends JpaRepository<StockTradeTransaction, Long> {

    Optional<StockTradeTransaction> findByExternalExecutionId(String externalExecutionId);

    List<StockTradeTransaction> findByInvestment_IdOrderByExecutedAtDesc(Long investmentId);

    @Query("""
            select t
            from StockTradeTransaction t
            join fetch t.investment i
            join fetch t.stock
            where i.user.id = :userId
            order by t.executedAt desc
            """)
    List<StockTradeTransaction> findByUserIdOrderByExecutedAtDesc(@Param("userId") Long userId);

    List<StockTradeTransaction> findByOrder_IdOrderByExecutedAtDesc(Long orderId);
}
