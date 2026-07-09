package com.finmate.service.stock.trading;

import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.event.StockOrderActivatedEvent;
import com.finmate.domain.stock.trading.event.StockOrderClosedEvent;
import com.finmate.domain.stock.trading.event.StockReservationActivatedEvent;
import com.finmate.domain.stock.trading.event.StockReservationClosedEvent;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import com.finmate.service.stock.realtime.StockRealtimeSubscriptionManager;
import com.finmate.service.stock.realtime.StockRealtimeSubscriptionPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// 주문/예약주 때문에 실시 시세 감시가 필요한 종목 서버가 시작하는 시점에 복구하고,
// 주문/예약주문의 활성화/종료 이벤트에 따라 실시간 구독을 증가/감소시키는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
// ApplicationRunner를 구현하면 스프링 애플리케이션 실행된 직후에 run()메서드가 자동으로 실행된다.
public class StockTradingRealtimeSubscriptionService implements ApplicationRunner {
    private static final List<StockOrderStatus> ACTIVE_ORDER_STATUSES = // 활성 주문 상태 정의
            List.of(StockOrderStatus.SUBMITTED, StockOrderStatus.PARTIALLY_FILLED);

    private final StockRealtimeSubscriptionManager subscriptionManager; // 실제 실시간 시세 구독/해제를 담당 (각 종목에 대한 구독 타입별 구독자 수를 관리)
    private final StockOrderRepository stockOrderRepository;
    private final StockOrderReservationRepository stockOrderReservationRepository;

    // 스프링 애플리케이션이 시작된 직후에 자동으로 실행되는 메서드
    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        // 활성 주문 상태의 종목들을 조회하여, 구독을 추가한다
        stockOrderRepository.findByStatusIn(ACTIVE_ORDER_STATUSES).stream()
                .map(StockOrder::getStock)
                .map(stock -> stock.getId())
                .forEach(stockId -> safeSubscribe(
                        stockId,
                        StockRealtimeSubscriptionPurpose.ACTIVE_ORDER));
        // 활성 예약 주문 상태의 종목들을 조회하여, 구독을 추가한다.
        stockOrderReservationRepository.findByStatusWithStock(StockOrderReservationStatus.ACTIVE).stream()
                .map(StockOrderReservation::getStock)
                .map(stock -> stock.getId())
                .forEach(stockId -> safeSubscribe(
                        stockId,
                        StockRealtimeSubscriptionPurpose.ACTIVE_RESERVATION));
    }
    // 주문 활성화 이벤트 처리 -> 주문이 들어온 종목에 대하여 구독 추가
    // 이때 실제 주문 정보가 DB에 반영된 직후에 이벤트를 처리하기 위해서 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) 해당 설정을 추가
    // 주문 요청 / 주문 취소등은 트랜잭션 내에서 수행되기 떄문에, 이벤트 자체도 트랜잭션 내에서 호출된다.
    // 이때 트랜잭션이 커밋되기 전에 이벤트핸들러가 호출되면, 트랜잭션이 롤백되었을때 정합성이 깨질 수 있기 때문에 트랜잭션이 커밋된 직후에 이벤트핸들러가 동작하도록 설정한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderActivated(StockOrderActivatedEvent event) {
        safeSubscribe(event.stockId(), StockRealtimeSubscriptionPurpose.ACTIVE_ORDER);
    }
    // 주문 종료 이벤트 처리 -> 주문이 종료되면 해당 종목에 대하여 구독 해제
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderClosed(StockOrderClosedEvent event) {
        safeUnsubscribe(event.stockId(), StockRealtimeSubscriptionPurpose.ACTIVE_ORDER);
    }
    // 예약 주문 활성화 이벤트 처리 -> 예약 주문이 들어오면 해당 종목에 대하여 구독 추가
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationActivated(StockReservationActivatedEvent event) {
        safeSubscribe(event.stockId(), StockRealtimeSubscriptionPurpose.ACTIVE_RESERVATION);
    }
    // 예약 주문 종료 이벤트 처리 -> 예약 주문이 종료되면 해당 종목에 대하여 구독 해제
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationClosed(StockReservationClosedEvent event) {
        safeUnsubscribe(event.stockId(), StockRealtimeSubscriptionPurpose.ACTIVE_RESERVATION);
    }

    // 종목을 목적에 맞게 구독하는 메서드
    private void safeSubscribe(Long stockId, StockRealtimeSubscriptionPurpose purpose) {
        try {
            subscriptionManager.subscribeStock(stockId, purpose);
        } catch (RuntimeException e) {
            log.warn("주식 거래용 실시간 구독에 실패했습니다. stockId={}, purpose={}", stockId, purpose, e);
        }
    }
    // 종목을 목적에 맞는 구독을 해제하는 메서드
    private void safeUnsubscribe(Long stockId, StockRealtimeSubscriptionPurpose purpose) {
        try {
            subscriptionManager.unsubscribeStock(stockId, purpose);
        } catch (RuntimeException e) {
            log.warn("주식 거래용 실시간 구독 해제에 실패했습니다. stockId={}, purpose={}", stockId, purpose, e);
        }
    }
}
