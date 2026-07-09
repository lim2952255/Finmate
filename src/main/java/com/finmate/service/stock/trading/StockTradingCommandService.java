package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.trading.StockOrderRequest;
import com.finmate.domain.stock.dto.trading.StockOrderReservationRequest;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.stock.trading.event.StockOrderActivatedEvent;
import com.finmate.domain.stock.trading.event.StockOrderClosedEvent;
import com.finmate.domain.stock.trading.event.StockReservationActivatedEvent;
import com.finmate.domain.stock.trading.event.StockReservationClosedEvent;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeOrderPrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizePositivePrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeRequiredQuantity;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validateOrderExpiration;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 일반 주문 / 예약 주문 접수 및 취소를 처리하는 서비스
@Service
@RequiredArgsConstructor
public class StockTradingCommandService {
    private static final List<StockOrderStatus> ACTIVE_ORDER_STATUSES = // 주문 활성화 상태
            List.of(StockOrderStatus.SUBMITTED, StockOrderStatus.PARTIALLY_FILLED);

    private final StockOrderRepository stockOrderRepository;
    private final StockOrderReservationRepository stockOrderReservationRepository;
    private final StockTradingLookupService lookupService;
    private final StockTradingAssetService assetService; // 주식 주문시에 필요한 종목 수량 또는 예수금에 lock을 걸고, 주문 종료 / 만료시에 lock을 해제하는 서비스
    private final StockTradingExecutionService executionService; // 실시간 체결 기준 가격을 기반으로 주식 주문 체결여부 판단 및 실제로 체결을 수행하는 서비스
    private final ApplicationEventPublisher eventPublisher; // 이벤트 발생기 (스프링에서 기본적으로 제공하는 스프링 빈)

    // 일반 주문이 둘어오면, 이를 처리하는 메서드
    @Transactional
    public StockOrder submitOrder(Long userId, StockOrderRequest request) {
        validateRequired(request, "주문 요청은 필수입니다.");
        Stock stock = lookupService.findStock(request.getStockId());
        Investment investment = lookupService.findOwnedInvestmentForUpdate(userId, request.getInvestmentId()); // 주문할때 사용된 증권 계좌
        CurrencyCode currencyCode = lookupService.currencyCode(stock);
        lookupService.validateTradable(stock);

        BigDecimal quantity = normalizeRequiredQuantity(request.getQuantity()); // 주문 수량
        StockOrderSide side = lookupService.requireSide(request.getSide()); // 매수 주문 or 매도 주문
        StockOrderType orderType = lookupService.requireOrderType(request.getOrderType()); // 시장가 주문 or 지정가 주문
        BigDecimal orderPrice = normalizeOrderPrice(currencyCode, orderType, request.getOrderPrice()); // 주문 가격
        validateOrderExpiration(orderType, request.getExpiresAt()); // 주문 만료기한이 유효한지 검사
        StockTradingAssetService.ReservedAsset reservedAsset = assetService.reserveAsset( // 종목에 대한 주문을 접수하기전, 매수의 경우 예수금에 lock을 걸고, 매도의 경우, 종목 수량에 lock을 건다.
                investment,
                stock,
                side,
                orderType,
                quantity,
                orderPrice,
                null);

        StockOrder order = StockOrder.create( // 주문 접수서
                UUID.randomUUID().toString(), // 만약 매우 낮은 확률로 주문번호(UUID)가 중복되더라도, 트랜잭션이 롤백되면서 주문이 무효되기 때문에 안전하다.
                investment,
                stock,
                null,
                side,
                orderType,
                currencyCode,
                quantity,
                orderPrice,
                reservedAsset.cashAmount(),
                reservedAsset.stockQuantity(),
                request.getExpiresAt());
        order.markSubmitted(); // 주문 접수 처리
        stockOrderRepository.save(order);

        // 만약 주문이 바로 체결되지 않는다면, 종목의 시세를 실시간으로 검사해야 하기 때문에, 종목을 구독하는 이벤트를 발생시킨다.
        if (!executionService.executeOrderIfPossible(order, false)) {
            eventPublisher.publishEvent(new StockOrderActivatedEvent(stock.getId()));
        }

        return order;
    }

    // 예약 주문 처리 메서드
    @Transactional
    public StockOrderReservation submitReservation(Long userId, StockOrderReservationRequest request) {
        validateRequired(request, "예약 주문 요청 필수입니다.");
        Stock stock = lookupService.findStock(request.getStockId());
        Investment investment = lookupService.findOwnedInvestmentForUpdate(userId, request.getInvestmentId());
        CurrencyCode currencyCode = lookupService.currencyCode(stock);
        lookupService.validateTradable(stock);

        BigDecimal quantity = normalizeRequiredQuantity(request.getQuantity());
        StockOrderSide side = lookupService.requireSide(request.getSide()); // 매수 or 매도
        StockOrderType orderType = lookupService.requireOrderType(request.getOrderType()); // 시장가 주문 or 지정가 주문
        BigDecimal triggerPrice = normalizePositivePrice(currencyCode, request.getTriggerPrice(), "예약 기준 가격은 필수입니다."); // 예약 기준 가격
        BigDecimal orderPrice = normalizeOrderPrice(currencyCode, orderType, request.getOrderPrice()); // 지정가
        validateOrderExpiration(orderType, request.getExpiresAt()); // 주문 만료기한이 유효한지 검사
        StockTradingAssetService.ReservedAsset reservedAsset = assetService.reserveAsset( // 예약 주문에 필요한 종목 수량 또는 예수금에 lock을 거는 메서드
                investment,
                stock,
                side,
                orderType,
                quantity,
                orderPrice,
                triggerPrice);

        // 예약 주문정보 추가
        StockOrderReservation reservation = StockOrderReservation.create(
                UUID.randomUUID().toString(),
                investment,
                stock,
                side,
                orderType,
                request.getTriggerCondition(),
                currencyCode,
                quantity,
                triggerPrice,
                orderPrice,
                reservedAsset.cashAmount(),
                reservedAsset.stockQuantity(),
                request.getExpiresAt());
        stockOrderReservationRepository.save(reservation);
        eventPublisher.publishEvent(new StockReservationActivatedEvent(stock.getId())); // 예약 주문의 경우, 실시간 시세를 계속 감시해야 하기 때문에 종목을 구독하는 이벤트를 발생시킨다.
        return reservation;
    }

    // 일반 주문 취소
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        StockOrder order = stockOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        lookupService.validateOwnedInvestment(userId, order.getInvestment());
        if (!ACTIVE_ORDER_STATUSES.contains(order.getStatus())) { // 활성 상태의 주문만 취소 가능
            throw new RuntimeException("활성 상태의 주문만 취소가 가능합니다.");
        }

        assetService.releaseOrderAsset(order); // 일반 주문에 걸려있는 예수금 또는 종목 수량에 대한 lock을 해제한다.
        order.cancelRemaining(); // 남은 주문을 취소한다.
        // 주문 취소 이벤트를 발생시킨다. (이벤트리스너가 종목에 대한 구독을 취소한다.)
        eventPublisher.publishEvent(new StockOrderClosedEvent(order.getStock().getId()));
    }

    // 예약 주문 취소
    @Transactional
    public void cancelReservation(Long userId, Long reservationId) {
        StockOrderReservation reservation = stockOrderReservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("예약 주문을 찾을 수 없습니다."));
        lookupService.validateOwnedInvestment(userId, reservation.getInvestment());
        if (reservation.getStatus() != StockOrderReservationStatus.ACTIVE) {
            throw new RuntimeException("활성 상태의 예약 주문만 취소할 수 있습니다.");
        }

        assetService.releaseReservationAsset(reservation); // 예약 주문에 걸려있는 예수금 또는 종목 수량에 대한 lock을 해제한다.
        reservation.cancel(); // 예약 주문을 취소한다.
        // 예약 주문 취소 이벤트를 발생시킨다. (이벤트리스너가 예약 종목에 대한 구독을 취소한다.)
        eventPublisher.publishEvent(new StockReservationClosedEvent(reservation.getStock().getId()));
    }
}
