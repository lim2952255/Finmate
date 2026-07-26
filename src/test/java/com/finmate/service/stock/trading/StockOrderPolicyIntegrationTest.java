package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.StockSecurityType;
import com.finmate.domain.stock.dto.trading.StockOrderRequest;
import com.finmate.domain.stock.dto.trading.StockOrderReservationRequest;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.user.User;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import com.finmate.service.stock.realtime.StockRealtimeSubscriptionManager;
import com.finmate.service.stock.realtime.StockRealtimeSubscriptionPurpose;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FINANTIAL_TEST ORD-001~005, ORD-015의 주문 접수 정책을 실제 MySQL 저장 경계에서 검증한다.
 * 외부 KIS 연결 대신 내부 시세 실행 경계와 구독 관리자를 mock으로 대체한다.
 */
// 해당 테스트에서만 일시적으로 StockOrderExpirationScheduler가 동작하도록 설정한다.
@TestPropertySource(properties = {
        "finmate.trading.expiration-enabled=true",
        "finmate.trading.expiration-interval-millis=86400000",
        "finmate.trading.expiration-initial-delay-millis=86400000"
})
class StockOrderPolicyIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockOrderRepository stockOrderRepository;

    @Autowired
    private StockOrderReservationRepository reservationRepository;

    // StockOrderExpirationScheduler를 등록한다.
    @Autowired
    private StockOrderExpirationScheduler expirationScheduler;

    // 실제 기능은 필요없지만, 연관관계 주입을 위해 MokckitoBean을 사용한다.
    @MockitoBean
    private StockTradingLookupService lookupService;

    @MockitoBean
    private StockTradingAssetService assetService;

    @MockitoBean
    private StockTradingExecutionService executionService;

    @MockitoBean
    private StockRealtimeSubscriptionManager subscriptionManager;

    private User user;
    private Investment investment;
    private Stock kospiStock;

    // 매 테스트가 실행되기 전에 호출되며, 테스트에 필요한 데이터를 준비한다.
    @BeforeEach
    void setUpPolicyFixtures() {
        // MockitoBean에 설정되었던 다른 기능들을 초기화한다.
        reset(lookupService, assetService, executionService, subscriptionManager);

        // 테스트용 사용자, 증권계좌, 코스피 종목을 생성한다.
        user = persistUser("stock-policy-user");
        investment = persistInvestment(user, "910000000001", SecuritiesCompanyCode.KOREA_INVESTMENT);
        kospiStock = persistStock("005930", "KR7005930003", StockMarketType.KOSPI, "KRW");

        // MockitoBean들에 기능을 추가한다.
        // lockupService를 통해, 종목, 증권계좌, 통화, 주문 사이드등을 조회할 수 있도록 한다.
        when(lookupService.findStock(kospiStock.getId())).thenReturn(kospiStock);
        when(lookupService.findOwnedInvestmentForUpdate(user.getId(), investment.getId())).thenReturn(investment);
        when(lookupService.currencyCode(kospiStock)).thenReturn(CurrencyCode.KRW);
        when(lookupService.requireSide(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookupService.requireOrderType(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // 주문을 접수하기 전에 주문에 필요한 금액과 수량에 lock을 거는 메서드 기능 추가
        when(assetService.reserveAsset(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new StockTradingAssetService.ReservedAsset(new BigDecimal("10000"), BigDecimal.ZERO));
        // 주문을 접수하고, 체결이 가능하면 체결까지 수행하는 메서드 기능 추가
        when(executionService.executeOrderIfPossible(any(), eq(false))).thenReturn(false);
    }

    @Test
    @DisplayName("ORD-001: 일반 주문은 시장 거래시간 검증을 통과한 뒤에만 저장한다")
    void ord001_regularOrderChecksTradingHoursBeforePersisting() {
        // 주문을 접수하고, 체결이 가능하면 체결한다.
        // 이때 주문을 접수하고나면, 내부에서 lookupService.validateTradingTime을 호출해 현재 주문 체결이 가능한 시간대인지 조사한다.
        // lookupService.validateTradingTime은 현재 주문가능 시간대가 아니면 예외를 발생시키는 메서드이다.
        // 하지만 현재 lookupService는 moockitoBean이기 때문에 validateTradingTime이 아무작업도 수행되지 않으며, 따라서 예외가 발생하지 않아 주문이 접수된다.
        StockOrder order = submitOrder(marketOrder(kospiStock, investment));

        // 이때 의존관계를 MockitoBean을 넣었기 때문에, 실제로 주문이 체결되지는 않고, 미체결상태로 남게된다.
        // 주문이 미체결상태인지를 검증한다.
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.SUBMITTED);
        // 주문이 저장되었는지를 검사한다.
        assertThat(stockOrderRepository.findById(order.getId())).isPresent();
        // lookupService의 validateTradingTime 메서드가 호출되었는지를 검사한다.
        verify(lookupService).validateTradingTime(kospiStock);
    }

    @Test
    @DisplayName("ORD-001: 장외 일반 주문 거부 시 주문과 자산 잠금은 생성되지 않는다")
    void ord001_outsideHoursRegularOrderLeavesNoOrderOrAssetLock() {
        // lookupService.validateTradingTime을 호출하게 되면 예외가 발생하도록 설정한다(장외 주문을 가정)
        doThrow(new RuntimeException("장외 주문 거부"))
                .when(lookupService).validateTradingTime(kospiStock);

        // 따라서 주문을 접수하고자 할때, 내부에서 lookupService.validateTradingTime을 호출하여, 주문가능시간대인지 검사하는데, 예외가 발생해야 한다.
        assertThatThrownBy(() -> submitOrder(marketOrder(kospiStock, investment)))
                .hasMessageContaining("장외 주문 거부");

        // 주문 접수시점에 예외가 발생했기 대문에 StockOrderRepository에 주문 건수가 저장되면 안된다.
        assertThat(stockOrderRepository.count()).isZero();
        // assetService.reserveAsset을 호출하여 금액과 보유종목에 lock을 걸기전에 예외가 발생하기 때문에, 해당 메서드가 호출되면 안된다.
        verify(assetService, never()).reserveAsset(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ORD-002 계약 실패 증거: 예약주문 등록은 장외에도 허용하고 자산을 잠가야 한다")
    void ord002_reservationRegistrationIsAllowedOutsideTradingHours() {
        // 현재 시간대를 장외 시간대로 가정한다.
        doThrow(new RuntimeException("현재 구현은 예약주문도 장외에 거부한다"))
                .when(lookupService).validateTradingTime(kospiStock);

        // 예약주문의 경우에는 장외 시간대에도 예약주문 접수를 허용해야 해야 하므로, 내부에서 lookupService를 호출할 필요가 없으며, 따라서 예외가 발생하면 안된다.
        // 하지만 지금은 예약주문 등록시점에도 정외 시간대인지를 체크하기 때문에 테스트에 실패한다.
        StockOrderReservation reservation = assertDoesNotThrow(
                () -> submitReservation(reservationRequest(kospiStock, investment, StockOrderType.LIMIT,
                        LocalDateTime.now().plusDays(1))));

        // 예약주문 접수의 경우, 장외시간대인지를 검사를 하면 안된다.
        verify(lookupService, never()).validateTradingTime(kospiStock);
        // 예약주문이 접수되며, assetService.reserveAsset 메서드를 호출되는지를 검사한다.
        assertThat(reservationRepository.findById(reservation.getId())).isPresent();
        verify(assetService).reserveAsset(any(), eq(kospiStock), eq(StockOrderSide.BUY),
                eq(StockOrderType.LIMIT), any(), any(), any());
    }

    @Test
    @DisplayName("ORD-002: 활성 예약주문은 장외 여부를 다시 검사하지 않고 취소하며 잠금을 해제한다")
    void ord002_activeReservationCanBeCanceledOutsideTradingHours() {
        // 예약주문 접수
        StockOrderReservation reservation = submitReservation(
                reservationRequest(kospiStock, investment, StockOrderType.LIMIT, LocalDateTime.now().plusDays(1)));
        reset(assetService);
        // lookupService.validateTradingTime을 호출하면 예외가 발생하게 기능을 추가한다.
        doThrow(new RuntimeException("취소에서는 호출되면 안 됨"))
                .when(lookupService).validateTradingTime(kospiStock);

        // 예약을 취소한다.
        transactionTemplate.executeWithoutResult(status ->
                commandService.cancelReservation(user.getId(), reservation.getId()));

        // 예약이 성공적으로 취소되었는지를 검사한다.
        assertThat(reservationRepository.findById(reservation.getId()))
                .get()
                .extracting(StockOrderReservation::getStatus)
                .isEqualTo(StockOrderReservationStatus.CANCELED);
        // 예약을 취소할때에는 lookupService.validateTradingTime을 호출하지 않는지를 검사한다.
        verify(assetService).releaseReservationAsset(any(StockOrderReservation.class));
    }

    @Test
    @DisplayName("ORD-003: 일반 시장가 주문은 만료기한 없이 저장한다")
    void ord003_marketOrderHasNoExpiration() {
        // 일반 시장가 주문을 접수한다.
        StockOrder order = submitOrder(marketOrder(kospiStock, investment));

        // 일반 시장가 주문은 기본적으로 즉시체결이기 떄문에 만료기한이 없어야 한다.
        assertThat(order.getOrderType()).isEqualTo(StockOrderType.MARKET);
        assertThat(order.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("ORD-003: 일반 지정가 주문은 미래 만료기한이 필수다")
    void ord003_limitOrderWithoutExpirationIsRejectedBeforeAssetLock() {
        // 일반 지정가 주문은 만료기한이 필수이다. 하지만 현재는 만료기한을 null로 입력
        StockOrderRequest request = limitOrder(kospiStock, investment, null);

        // 일반 지정가 주문의 만료기한을 null로 설정했기 때문에 예외가 발생해야 한다.
        assertThatThrownBy(() -> submitOrder(request))
                .hasMessageContaining("만료시각은 필수");

        // 예외가 발생해서 주문이 접수되지 않았는지를 검사한다.
        assertThat(stockOrderRepository.count()).isZero();
        verify(assetService, never()).reserveAsset(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ORD-003 계약 실패 증거: 시장가형 예약도 미래 만료기한이 필수다")
    void ord003_marketReservationWithoutExpirationIsRejected() {
        // 시장가형 예약주문을 접수할떄에도 만료기한을 설정해야 한다. 하지만 현재는 만료기한을 null로 설정한다.
        StockOrderReservationRequest request = reservationRequest(
                kospiStock, investment, StockOrderType.MARKET, null);

        // 시장가형 예약주문을 접수할때 만료기한이 설정되지 않았기 때문에 예외가 발생해야 한다.
        assertThatThrownBy(() -> submitReservation(request))
                .hasMessageContaining("만료");

        // 예외가 발생해서 예약주문이 접수되지 않았는지를 검사한다.
        assertThat(reservationRepository.count()).isZero();
        verify(assetService, never()).reserveAsset(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ORD-003 계약 실패 증거: 시장가형 예약의 과거 만료기한도 거부한다")
    void ord003_marketReservationWithPastExpirationIsRejected() {
        // 시장가형 예약주문을 접수할 떄, 예약주문의 만료기한을 현재보다 과거로 설정하면 예외가 발생해야 한다.
        StockOrderReservationRequest request = reservationRequest(
                kospiStock, investment, StockOrderType.MARKET, LocalDateTime.now().minusSeconds(1));

        // 시장가형 예약주문을 접수할때 만료기한이 현재보다 과거로 설정되어 있기 때문에 예외가 발생해야 한다.
        assertThatThrownBy(() -> submitReservation(request))
                .hasMessageContaining("현재 시각 이후");

        // 예약주문이 접수되지 않았는지를 검사한다.
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    @DisplayName("ORD-003: 미래 만료기한이 있는 지정가형 예약은 만료기한을 그대로 저장한다")
    void ord003_limitReservationPersistsFutureExpiration() {
        // 현재보다 미래기한으로 설정한다.
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1).withNano(0);

        // 예약주문을 접수한다.
        StockOrderReservation reservation = submitReservation(
                reservationRequest(kospiStock, investment, StockOrderType.LIMIT, expiresAt));

        // 예약 만료기한이 현재보다 미래이기 떄문에 예약주문이 접수되어야 한다.
        assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reservation.getStatus()).isEqualTo(StockOrderReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("ORD-004: 실시간 payload가 없어도 지정가 주문은 주기 검사로 만료된다")
    void ord004_limitOrderExpiresWithoutRealtimePayload() {
        // 접수 정책을 만족하는 미래 만료기한으로 지정가 주문을 접수한다. (미래 만료기한 최소기준: 현재시간 + 5분)
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5).plusSeconds(1);
        StockOrder order = submitOrder(limitOrder(kospiStock, investment, expiresAt));

        // 서버 중단 중 만료된 주문이 재시작 후 조회되는 상황처럼 DB의 만료시각을 과거로 이동한다.
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("""
                                update StockOrder o
                                set o.expiresAt = :expiresAt
                                where o.id = :orderId
                                """)
                        .setParameter("expiresAt", LocalDateTime.now().minusSeconds(1))
                        // 주문의 만료시각을 과거로 설정한다. -> 주문이 만료상태가 된다.
                        .setParameter("orderId", order.getId())
                        .executeUpdate());

        // 재시작 직후 실행되는 것과 같은 만료 검사를 수행한다.
        expirationScheduler.expireOverdueOrdersAndReservations();

        // 주문이 만료상태로 변해야 한다.
        StockOrder persisted = stockOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(StockOrderStatus.EXPIRED);
        verify(assetService).releaseOrderAsset(any(StockOrder.class));
    }

    // 애플리케이션을 중단했다가 다시 실행하는 동안 주문이 만료된 경우를 처리한다.
    @Test
    @DisplayName("ORD-004: 서버 중단 중 만료된 예약주문도 시작 검사에서 만료된다")
    void ord004_expiredReservationIsRecoveredAfterRestart() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5).plusSeconds(1);
        StockOrderReservation reservation = submitReservation(
                reservationRequest(kospiStock, investment, StockOrderType.MARKET, expiresAt));

        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("""
                                update StockOrderReservation r
                                set r.expiresAt = :expiresAt
                                where r.id = :reservationId
                                """)
                        .setParameter("expiresAt", LocalDateTime.now().minusSeconds(1))
                        // 주문 만료기한을 현재시간보다 적게 설정함으로서 애플리케이션이 종료되어있는동안 만료기한이 지난 상황을 시뮬레이션한다.
                        .setParameter("reservationId", reservation.getId())
                        .executeUpdate());

        expirationScheduler.expireOverdueOrdersAndReservations();

        // 주문이 만료처리가 되어야 한다.
        StockOrderReservation persisted = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(StockOrderReservationStatus.EXPIRED);
        verify(assetService).releaseReservationAsset(any(StockOrderReservation.class));
    }

    @Test
    @DisplayName("ORD-005: KOSPI와 KOSDAQ은 KRW, NASDAQ은 USD로 주문을 저장한다")
    void ord005_marketSelectsSettlementCurrency() {
        // 시장에 맞는 통화를 기반으로 거래하는지를 검사한다.
        Stock kosdaq = persistStock("035720", "KR7035720002", StockMarketType.KOSDAQ, "KRW");
        Stock nasdaq = persistStock("AAPL", null, StockMarketType.NASDAQ, "USD");
        when(lookupService.findStock(kosdaq.getId())).thenReturn(kosdaq);
        when(lookupService.findStock(nasdaq.getId())).thenReturn(nasdaq);
        when(lookupService.currencyCode(kosdaq)).thenReturn(CurrencyCode.KRW);
        when(lookupService.currencyCode(nasdaq)).thenReturn(CurrencyCode.USD);

        // KOSPI, KOSDAQ, NASDAQ 종목을 주문할때 각 시장에 맞는 통화가 사용되는지를 검사한다.
        StockOrder kospiOrder = submitOrder(marketOrder(kospiStock, investment));
        StockOrder kosdaqOrder = submitOrder(marketOrder(kosdaq, investment));
        StockOrder nasdaqOrder = submitOrder(marketOrder(nasdaq, investment));

        assertThat(kospiOrder.getCurrencyCode()).isEqualTo(CurrencyCode.KRW);
        assertThat(kosdaqOrder.getCurrencyCode()).isEqualTo(CurrencyCode.KRW);
        assertThat(nasdaqOrder.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    @DisplayName("ORD-015: 주문 접수는 내부 모의 주문만 저장하고 KIS 외부 주문 경계를 호출하지 않는다")
    void ord015_orderAcceptanceStaysInsideSimulationBoundary() {
        // 시장가 주문을 접수한다.
        StockOrder order = submitOrder(marketOrder(kospiStock, investment));

        // 주문이 접수되었는지를 검사한다.
        assertThat(order.getOrderNumber()).isNotBlank();
        assertThat(stockOrderRepository.findByOrderNumber(order.getOrderNumber())).isPresent();
        // 주문 접수 메서드가 호출되었는지를 검사한다.
        verify(executionService).executeOrderIfPossible(order, false);
        // 미체결 활성 주문의 체결 조건을 계속 확인하기 위해 해당 종목의 실시간 시세를 구독했는지 확인해.
        verify(subscriptionManager).subscribeStock(
                kospiStock.getId(), StockRealtimeSubscriptionPurpose.ACTIVE_ORDER);
    }

    // 트랜잭션내에서 일반 주문을 접수하고, 체결이 가능하면 주문 체결까지 수행한다.
    private StockOrder submitOrder(StockOrderRequest request) {
        return transactionTemplate.execute(status -> commandService.submitOrder(user.getId(), request));
    }
    // 트랜잭션내에서 예약 주문을 접수한다.
    private StockOrderReservation submitReservation(StockOrderReservationRequest request) {
        return transactionTemplate.execute(status -> commandService.submitReservation(user.getId(), request));
    }

    // 일반 시장가 주문 접수 객체(StockOrderRequest)를 생성한다.
    private StockOrderRequest marketOrder(Stock stock, Investment targetInvestment) {
        StockOrderRequest request = new StockOrderRequest();
        request.setInvestmentId(targetInvestment.getId());
        request.setStockId(stock.getId());
        request.setSide(StockOrderSide.BUY); // 매수주문
        request.setOrderType(StockOrderType.MARKET); //시장가 주문
        request.setQuantity(BigDecimal.ONE); // 매수수량: 1개
        return request;
    }

    // 일반 지정가 주문 접수 객체(StockOrderRequest)를 생성한다.
    private StockOrderRequest limitOrder(Stock stock, Investment targetInvestment, LocalDateTime expiresAt) {
        StockOrderRequest request = marketOrder(stock, targetInvestment); // 일반 시장가 주문 메서드를 그대로 호출
        request.setOrderType(StockOrderType.LIMIT); // 지정가 주문으로 변경
        request.setOrderPrice(new BigDecimal("10000")); // 지정가: 10000원
        request.setExpiresAt(expiresAt); // 만료기한 설정
        return request;
    }

    // 예약 주문 접수 객체(StockOrderReservationRequest)를 생성한다.
    private StockOrderReservationRequest reservationRequest(Stock stock,
                                                            Investment targetInvestment,
                                                            StockOrderType orderType,
                                                            LocalDateTime expiresAt) {
        StockOrderReservationRequest request = new StockOrderReservationRequest();
        request.setInvestmentId(targetInvestment.getId());
        request.setStockId(stock.getId());
        request.setSide(StockOrderSide.BUY); // 매수주문
        request.setOrderType(orderType); // 시장가 주문 or 지정가 주문
        request.setTriggerCondition(StockOrderTriggerCondition.PRICE_AT_OR_BELOW); // 예약기준보다 가격이 낮아졌을때 trigger된다.
        request.setQuantity(BigDecimal.ONE); // 매수수량: 1개
        request.setTriggerPrice(new BigDecimal("9000")); // 예약기준가격: 9000원
        request.setOrderPrice(orderType == StockOrderType.LIMIT ? new BigDecimal("9000") : null); //지정가 주문일경우, 매수가격을 10000원으로 설정
        request.setExpiresAt(expiresAt); // 만료기한 설정
        return request;
    }

    // 테스트에 사용할 종목을 등록한다.
    private Stock persistStock(String symbol,
                               String standardCode,
                               StockMarketType marketType,
                               String currency) {
        return transactionTemplate.execute(status -> {
            Stock stock = Stock.create(
                    symbol,
                    symbol,
                    standardCode,
                    symbol + " 테스트 종목",
                    symbol,
                    marketType,
                    marketType == StockMarketType.NASDAQ ? "US" : "KR",
                    marketType == StockMarketType.NASDAQ ? "NAS" : "KRX",
                    currency,
                    StockSecurityType.COMMON_STOCK,
                    false,
                    null,
                    LocalDateTime.now());
            entityManager.persist(stock);
            entityManager.flush();
            return stock;
        });
    }
}
