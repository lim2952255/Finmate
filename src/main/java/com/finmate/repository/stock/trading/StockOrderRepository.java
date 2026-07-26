package com.finmate.repository.stock.trading;

import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

    Optional<StockOrder> findByOrderNumber(String orderNumber);

    Optional<StockOrder> findByReservation_Id(Long reservationId);

    List<StockOrder> findByInvestment_IdOrderByCreatedAtDesc(Long investmentId);

    // 주문들을 만료기한의 내림차순으로 내림차순으로 정렬해서 조회
    @Query("""
            select o
            from StockOrder o
            join fetch o.investment i
            join fetch o.stock
            where i.user.id = :userId
            order by o.createdAt desc
            """)
    List<StockOrder> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    List<StockOrder> findByInvestment_IdAndStatusInOrderByCreatedAtDesc(Long investmentId,
                                                                        List<StockOrderStatus> statuses);

    @Query("""
            select o
            from StockOrder o
            join fetch o.stock
            where o.status in :statuses
            """)
    List<StockOrder> findByStatusIn(@Param("statuses") List<StockOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            join fetch o.investment
            join fetch o.stock
            where o.id = :orderId
            """)
    Optional<StockOrder> findByIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            join fetch o.investment
            join fetch o.stock
            where o.status in :statuses
              and o.expiresAt is not null
              and o.expiresAt <= :now
            order by o.expiresAt asc, o.id asc
            """)
    List<StockOrder> findExpiredActiveForUpdate(@Param("statuses") List<StockOrderStatus> statuses,
                                                @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            join fetch o.investment
            join fetch o.stock
            where o.status in :statuses
              and o.stock.id = :stockId
            order by o.createdAt asc
            """)
    List<StockOrder> findActiveByStockIdForUpdate(@Param("stockId") Long stockId,
                                                  @Param("statuses") List<StockOrderStatus> statuses);
}
