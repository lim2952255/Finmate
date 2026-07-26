package com.finmate.repository.stock.trading;

import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
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
public interface StockOrderReservationRepository extends JpaRepository<StockOrderReservation, Long> {

    Optional<StockOrderReservation> findByReservationNumber(String reservationNumber);

    List<StockOrderReservation> findByInvestment_IdOrderByCreatedAtDesc(Long investmentId);

    // 주문의 만료기한을 내림차순으로 정렬해서 조회
    @Query("""
            select r
            from StockOrderReservation r
            join fetch r.investment i
            join fetch r.stock
            where i.user.id = :userId
            order by r.createdAt desc
            """)
    List<StockOrderReservation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    List<StockOrderReservation> findByStatus(StockOrderReservationStatus status);

    @Query("""
            select r
            from StockOrderReservation r
            join fetch r.stock
            where r.status = :status
            """)
    List<StockOrderReservation> findByStatusWithStock(@Param("status") StockOrderReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from StockOrderReservation r
            join fetch r.investment
            join fetch r.stock
            where r.id = :reservationId
            """)
    Optional<StockOrderReservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from StockOrderReservation r
            join fetch r.investment
            join fetch r.stock
            where r.status = :status
              and r.expiresAt <= :now
            order by r.expiresAt asc, r.id asc
            """)
    List<StockOrderReservation> findExpiredActiveForUpdate(
            @Param("status") StockOrderReservationStatus status,
            @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from StockOrderReservation r
            join fetch r.investment
            join fetch r.stock
            where r.status = :status
              and r.stock.id = :stockId
            order by r.createdAt asc
            """)
    List<StockOrderReservation> findByStockIdAndStatusForUpdate(@Param("stockId") Long stockId,
                                                                @Param("status") StockOrderReservationStatus status);
}
