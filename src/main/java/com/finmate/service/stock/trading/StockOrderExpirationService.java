package com.finmate.service.stock.trading;

import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.event.StockOrderClosedEvent;
import com.finmate.domain.stock.trading.event.StockReservationClosedEvent;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


// 주문들의 만료기한을 검사하고 실제 주문 만료처리를 한다.
@Service
@RequiredArgsConstructor
public class StockOrderExpirationService {
    // 활성상태의 주문목록을 조회한다. (만료 대상이 될 수 있는 일반 주문 상태)
    private static final List<StockOrderStatus> ACTIVE_ORDER_STATUSES =
            List.of(StockOrderStatus.SUBMITTED, StockOrderStatus.PARTIALLY_FILLED);

    private final StockOrderRepository stockOrderRepository; // 만료된 일반 주문 조회용
    private final StockOrderReservationRepository stockOrderReservationRepository; // 만료된 예약주문 조회용
    private final StockTradingAssetService assetService; // 묶여있던 예수금 또는 보유수량에 대한 lock을 해제하는 메서드
    private final ApplicationEventPublisher eventPublisher; // 주문 만료 사실을 다른 컴포넌트들에게 알리는 이벤트 발생기

    @Transactional
    public void expireOverdueOrdersAndReservations(LocalDateTime now) {
        // 예약주문 목록 조회
        List<StockOrderReservation> reservations =
                stockOrderReservationRepository.findExpiredActiveForUpdate(
                        StockOrderReservationStatus.ACTIVE,
                        now);
        // 예약주문들을 조회하며 주문기한이 만료된 예약 주문들을 만료시킨다.
        for (StockOrderReservation reservation : reservations) {
            expireReservationIfDue(reservation, now);
        }

        // 일반주문 목록 조회
        List<StockOrder> orders =
                stockOrderRepository.findExpiredActiveForUpdate(ACTIVE_ORDER_STATUSES, now);
        // 일반주문들을 조회하며 주문기한이 만료된 일반주문들을 만료시킨다.
        for (StockOrder order : orders) {
            expireOrderIfDue(order, now);
        }
    }

    // 예약주문의 만료기한 검증 및 처리
    boolean expireReservationIfDue(StockOrderReservation reservation, LocalDateTime now) {
        if (!reservation.isExpired(now)) {
            return false;
        }

        assetService.releaseReservationAsset(reservation);
        reservation.expire();
        // 예약 주문 만료 이벤트를 발생시킨다.( KIS API에 종목 구독을 해제하는 이벤트를 발생시킨다.)
        eventPublisher.publishEvent(new StockReservationClosedEvent(reservation.getStock().getId()));
        return true;
    }

    // 일반주문의 만료기한 검증 및 처리
    boolean expireOrderIfDue(StockOrder order, LocalDateTime now) {
        if (!order.isExpired(now)) {
            return false;
        }

        assetService.releaseOrderAsset(order);
        order.expireRemaining();
        // 일반 주문 만료 이벤트를 발생시킨다.( KIS API에 종목 구독을 해제하는 이벤트를 발생시킨다.)
        eventPublisher.publishEvent(new StockOrderClosedEvent(order.getStock().getId()));
        return true;
    }
}
