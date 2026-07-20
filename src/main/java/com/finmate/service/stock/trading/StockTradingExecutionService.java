package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.domain.stock.trading.StockTradingFeePolicy;
import com.finmate.domain.stock.trading.event.StockOrderActivatedEvent;
import com.finmate.domain.stock.trading.event.StockOrderClosedEvent;
import com.finmate.domain.stock.trading.event.StockReservationClosedEvent;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static com.finmate.domain.stock.trading.TradingAmountValidator.calculateAmount;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeCurrencyAmount;

// 실시간 체결 기준 금액을 기반으로 예약주문 or 지정가 주문의 체결 여부를 판단하고, 체결을 수행하는 서비스
@Service
@RequiredArgsConstructor
public class StockTradingExecutionService {
    private static final List<StockOrderStatus> ACTIVE_ORDER_STATUSES = // 주문 활성화 상태
            List.of(StockOrderStatus.SUBMITTED, StockOrderStatus.PARTIALLY_FILLED);

    private final StockOrderRepository stockOrderRepository;
    private final StockOrderReservationRepository stockOrderReservationRepository;
    private final StockTradeTransactionRepository stockTradeTransactionRepository;
    private final StockTradingRealtimePriceService realtimePriceService; // 실시간 체결기준 가격을 결정하는 서비스
    private final StockTradingLookupService lookupService;
    private final StockTradingAssetService assetService;
    private final ApplicationEventPublisher eventPublisher; // 이벤트 발생기 (스프링에서 기본적으로 제공하는 스프링 빈)

    // 실시간으로 주식의 가격이 변동될때마다 주문을 체결할지 여부를 결정하는 메서드
    @Transactional
    public void processRealtimeUpdate(Long stockId) {
        Stock stock = lookupService.findStock(stockId);
        boolean tradingTime = isTradingTime(stock);
        processReservations(stock, tradingTime); // 종목의 예약주문 체결 여부를 결정
        processOrders(stock, tradingTime); // 종목의 지정가 주문 체결 여부를 결정
    }

    // 종목의 예약주문 체결 여부를 결정한다.
    private void processReservations(Stock stock, boolean tradingTime) {
        // 해당 종목과 관련하여 활성상태의 예약주문들을 조회
        List<StockOrderReservation> reservations = stockOrderReservationRepository.findByStockIdAndStatusForUpdate(
                stock.getId(),
                StockOrderReservationStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();

        for (StockOrderReservation reservation : reservations) {
            // 예약기간이 만료된 경우
            if (reservation.isExpired(now)) {
                assetService.releaseReservationAsset(reservation);
                reservation.expire(); // 예약 만료 처리
                // 예약 주문 취소 이벤트를 발생시킨다. (이벤트 리스너가 해당 예약종목에 대한 구독을 취소한다.)
                eventPublisher.publishEvent(new StockReservationClosedEvent(stock.getId()));
                continue;
            }

            if (!tradingTime) {
                continue;
            }

            // 예약기간이 유효한 경우, realtimePriceService를 통해 실시간 체결 기준 가격을 받는다.
            BigDecimal triggerBasisPrice = realtimePriceService
                    .findExecutablePrice(stock, reservation.getSide())
                    .orElse(null);
            // 만약 실시간 체결 기준 가격이 예약 기준을 충족하지 못하면 예약 주문 체결 x
            if (triggerBasisPrice == null || !reservation.isTriggerSatisfied(triggerBasisPrice)) {
                continue;
            }

            // 예약 기준을 충족할 경우, 예약 주문을 실제 주문으로 접수한다.
            reservation.markTriggered(now);
            // 예약주문이 실제 주문으로 접수되었기 때문에, 예약 주문 취소 이벤트를 발생시킨다.
            eventPublisher.publishEvent(new StockReservationClosedEvent(stock.getId()));

            // 예약 주문 내용을 기반으로 실제 주문을 접수한다.
            StockOrder order = createOrderFromReservation(reservation);
            stockOrderRepository.save(order);
            // 예약 주문을 일반 주문으로 변환한 이후, 즉시 주문 체결이 가능하다면 일반 주문 접수 이벤트와 일반 주문 종료 이벤트를 발생시키는 오버헤드를 줄이기 위해 closeActiveSubscription 파라미터를 사용한다.
            if (!executeOrderIfPossible(order, false)) { // 주문이 아직 체결되지 않은 경우(지정가를 충족하지 못한 경우), 주문 접수 이벤트를 발생시킨다.
                eventPublisher.publishEvent(new StockOrderActivatedEvent(stock.getId()));
            }
        }
    }

    // 종목의 일반 주문 체결 여부를 결정한다. (지정가 주문)
    private void processOrders(Stock stock, boolean tradingTime) {
        // 해당 종목과 관련하여 활성 상태의 일반주문들을 조회
        List<StockOrder> orders = stockOrderRepository.findActiveByStockIdForUpdate(stock.getId(), ACTIVE_ORDER_STATUSES);
        LocalDateTime now = LocalDateTime.now();

        for (StockOrder order : orders) {
            // 지정가 주문의 기한이 만료된 경우
            if (order.isExpired(now)) {
                assetService.releaseOrderAsset(order);
                order.expireRemaining(); // 지정가 주문을 만료시킨다.
                // 지정가 주문이 만료되었으므로, 일반 주문 취소 이벤트를 발생시킨다.
                eventPublisher.publishEvent(new StockOrderClosedEvent(order.getStock().getId()));
                continue;
            }

            if (!tradingTime) {
                continue;
            }

            // 현재 지정가 주문이 체결가능한지 확인하고, 지정가 주문을 체결한다.
            executeOrderIfPossible(order, true);
        }
    }

    // 예약 정보를 기반으로 이를 일반 주문으로 변환해서 접수한다.
    private StockOrder createOrderFromReservation(StockOrderReservation reservation) {
        StockOrder order = StockOrder.create(
                UUID.randomUUID().toString(),
                reservation.getInvestment(),
                reservation.getStock(),
                reservation,
                reservation.getSide(),
                reservation.getOrderType(),
                reservation.getCurrencyCode(),
                reservation.getQuantity(),
                reservation.getOrderPrice(),
                reservation.getReservedCashAmount(),
                reservation.getReservedStockQuantity(),
                reservation.getExpiresAt());
        order.markSubmitted(); // 예약 주문 -> 일반 주문 변환 후 주문 접수처리
        return order;
    }

    // 실시간 체결 기준 가격을 기반으로 지정가 주문을 체결할지 여부를 결정한다.
    // 만약 체결 기준을 만족하는 경우에는 주문을 실제로 체결한다.
    // closeActiveSubscript가 true인 경우에는 종목구독을 한 상태에서 주문을 체결하는 것이기 때문에, 주문을 체결하고 난 다음에 구독해제 이벤트를 발생시켜야 한다.
    // 반면 closeActiveSubscript가 false인 경우에는 종목구독을 하지 않은 상태에서 주문을 체결하는 것이기 때문에, 주문을 체결하고 난 다음에도 구독해제 이벤트를 발생시키지 않는다.
    boolean executeOrderIfPossible(StockOrder order, boolean closeActiveSubscription) {
        if (!isTradingTime(order.getStock())) {
            return false;
        }

        // 실시간 체결 기준 가격(실시간 체결가 / 실시간 호가 기반)
        BigDecimal executionPrice = realtimePriceService
                .findExecutablePrice(order.getStock(), order.getSide())
                .orElse(null);
        // 지정가 주문의 체결 기준을 만족하는지 검사
        if (executionPrice == null || !isExecutable(order, executionPrice)) {
            return false;
        }

        // 지정가 주문의 체결 기준을 만족하는 경우, 주문 체결
        executeOrder(order, executionPrice);
        // closeActiveSubscription이 true로 설정되어 있으면, 종목의 일반 주문 체결 종료 이벤트를 발생시킨다.
        // closeActiveSubscription을 사용하는 이유는, 일반 주문의 경우에 주문 접수와 동시에 바로 체결되는 주문들도 있는데, 이런 종목들을 구독하고 구독 해제하는 오버헤드를 줄이기 위해,
        // closeActiveSubscription을 사용하여 즉시 체결되지 않는 지정가 주문의 경우에만 구독 및 구독해제를 수행하도록 설정한다.
        if (closeActiveSubscription) {
            eventPublisher.publishEvent(new StockOrderClosedEvent(order.getStock().getId()));
        }
        return true;
    }

    // 현재 시간이 종목을 거래할 수 있는 시간인지 확인
    private boolean isTradingTime(Stock stock) {
        return StockMarketSchedules.isTradingTime(stock.getMarketType(), ZonedDateTime.now());
    }

    // 주문이 체결가능한지 여부를 체결 기준 가격을 기반으로 검사한다.
    private boolean isExecutable(StockOrder order, BigDecimal executionPrice) {
        // 시장가는 즉시 체결 가능
        if (order.getOrderType() == StockOrderType.MARKET) {
            return true;
        }

        // 지정가 매수의 경우에는 체결 기준 가격이 지정가보다 낮아야 한다.
        if (order.getSide() == StockOrderSide.BUY) {
            return executionPrice.compareTo(order.getOrderPrice()) <= 0;
        }

        // 지정가 매도의 경우에는 체결 기준 가격이 지정가보다 높아야 한다.
        return executionPrice.compareTo(order.getOrderPrice()) >= 0;
    }

    // 체결 조건을 만족한 주문에 대해서 예수금, 보유수량, 주문상태, 거래내역을 반영하는 메서드
    private void executeOrder(StockOrder order, BigDecimal executionPrice) {
        BigDecimal executionQuantity = order.getRemainingQuantity(); // 주문 수량
        CurrencyCode currencyCode = order.getCurrencyCode(); // 거래 통화
        BigDecimal normalizedExecutionPrice = normalizeCurrencyAmount(currencyCode, executionPrice, RoundingMode.HALF_UP); // 체결가
        BigDecimal grossAmount = calculateAmount(currencyCode, normalizedExecutionPrice, executionQuantity, RoundingMode.HALF_UP); // 총 거래 금액 (수수료나 세금을 제외 순수 거래대금)
        StockTradingFeePolicy feePolicy = StockTradingFeePolicy.from(order.getStock().getMarketType()); // 종목 시장별 수수료/세금 정책
        BigDecimal commissionAmount = feePolicy.calculateCommissionAmount(currencyCode, grossAmount); // 거래 수수료
        BigDecimal taxAmount = feePolicy.calculateTaxAmount(currencyCode, order.getSide(), grossAmount); // 거래 세금
        BigDecimal netCashAmount = order.getSide() == StockOrderSide.BUY // 예수금에 반영될 금액
                ? grossAmount.add(commissionAmount) // 매수의 경우 거래대금 + 수수료를 내야한다.
                : grossAmount.subtract(commissionAmount).subtract(taxAmount); // 매도의 경우 거래대금 - 수수료 - 세금을 받는다.

        InvestmentCashBalance cashBalance = assetService.findCashBalanceForUpdate(order.getInvestment().getId(), currencyCode);
        StockHolding holding = assetService.findOrCreateHoldingForUpdate(order.getInvestment(), order.getStock(), currencyCode);
        BigDecimal cashBefore = cashBalance.getTotalBalance(); // 주문 체결 전 예수금
        BigDecimal holdingBefore = holding.getQuantity(); // 주문 체결 전 종목 수량

        if (order.getSide() == StockOrderSide.BUY) { // 매수의 경우, cashBalance에서 주문 체결결과를 예수금에 반영하고 lock을 해제한다.
            cashBalance.settleBuyFromLocked(order.getReservedCashAmount(), netCashAmount);
            holding.applyBuyExecution(executionQuantity, normalizedExecutionPrice); // 또한 StockHolding에서 보유중인 종목 수량과 평균단가를 update한다.
        } else {
            holding.applySellExecution(executionQuantity); // 매도의 경우, StockHolding에서 보유중인 종목 수량을 update하고, lock을 해제한다.
            cashBalance.deposit(netCashAmount); // 또한 cashBalance에서 주문 체결 결과를 예수금에 반영한다.
        }

        order.applyExecution(executionQuantity); // 접수된 주문을 체결(부분 체결)하고, 주문의 상태를 update한다.

        // 주문 체결 내역을 저장한다.
        stockTradeTransactionRepository.save(StockTradeTransaction.create(
                order.getInvestment(),
                order.getStock(),
                order,
                UUID.randomUUID().toString(),
                order.getSide(),
                currencyCode,
                executionQuantity,  // 체결 수량
                normalizedExecutionPrice, // 체결 기준 가격
                grossAmount, // 순수 거래대금
                commissionAmount, // 증권사 수수료
                taxAmount, // 세금
                netCashAmount, // 예수금에 실제로 반영되 최종 현금 변화량(거래대금에 수수료 및 세금을 반영한 금액)
                cashBefore, // 체결 전 예수금
                cashBalance.getTotalBalance(), // 체결 후 예수금
                holdingBefore, // 체결 전 종목 수량
                holding.getQuantity(), // 체결 후 종목 수량
                LocalDateTime.now()));
    }
}
