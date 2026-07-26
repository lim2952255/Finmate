package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderReservationStatus;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import com.finmate.service.stock.trading.StockSettlementIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StockSettlementTerminalIntegrationTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradingExecutionService executionService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockOrderReservationRepository reservationRepository;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    @Test
    @DisplayName("ORD-006/010: 매수 주문 취소는 잠금액을 한 번만 반환하고 두 번째 취소를 거부한다")
    void cancelOrderReleasesCashExactlyOnce() {
        // 테스트 데이터 생성 및 예수금은 100000원으로 설정
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 매수기준가격을 empty로 설정
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());
        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 매수 주문 생성(수량: 10개, 지정가: 1000) -> 이때 아직 매수기준가격이 empty이기 때문에 주문은 접수되었지만 미체결상태
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }

        // 미체결상태의 주문을 취소한다.
        commandService.cancelOrder(fixture.userId(), submitted.getId());

        // 이미 취소된 주문을 다시한번 취소하려고 하면 이를 거부하며 예외가 발생해야 한다.
        assertThatThrownBy(() -> commandService.cancelOrder(fixture.userId(), submitted.getId()))
                .hasMessage("활성 상태의 주문만 취소가 가능합니다.");

        // 취소된 주문 조회
        StockOrder canceled = orderRepository.findById(submitted.getId()).orElseThrow();
        InvestmentCashBalance cash = cashBalance(fixture);
        // 취소된 주문 상태와 취소 수량을 검증하고, 증권계좌에서 예수금이 차감되었는지 검증, 주문 원장이 생성되지 않았는지 검증
        assertThat(canceled.getStatus()).isEqualTo(StockOrderStatus.CANCELED);
        assertThat(canceled.getCanceledQuantity()).isEqualByComparingTo("10");
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("ORD-006/010: 만료 처리를 반복해도 주문 잠금액은 한 번만 반환된다")
    void expireOrderReleasesCashExactlyOnce() {
        // 테스트 데이터 생성 및 예수금은 100000원으로 설정
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 매수기준 가격을 empty로 설정
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());
        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 매수 주문 생성(수량: 10개, 지정가: 1000) -> 이때 아직 매수기준가격이 empty이기 때문에 주문은 접수되었지만 미체결상태
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }
        // 주문만료기한이 현재 시간을 넘긴경우
        jdbcTemplate.update("update stock_order set expires_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), submitted.getId());

        // ExecutionService의 processRealtimeUpdate를 호출한 경우, 해당 종목의 미체결 주문의 주문 만료기한이 지났기 때문에 주문을 만료처리한다.
        executionService.processRealtimeUpdate(fixture.stockId());
        // 이미 주문이 만료처리되었기 때문에 아무런 작업도 하지 않는다.
        executionService.processRealtimeUpdate(fixture.stockId());

        // 만료된 주문 검증
        StockOrder expired = orderRepository.findById(submitted.getId()).orElseThrow();
        InvestmentCashBalance cash = cashBalance(fixture);
        assertThat(expired.getStatus()).isEqualTo(StockOrderStatus.EXPIRED);
        assertThat(expired.getCanceledQuantity()).isEqualByComparingTo("10");
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("ORD-006/010: 예약 취소는 잠금액을 한 번만 반환하고 두 번째 종료 전이를 거부한다")
    void cancelReservationReleasesCashExactlyOnce() {
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        StockOrderReservation submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 예약 매수 주문 생성 (예약 수량: 10개, 예약기준 가격: 1000원, 지정가: 1000원)
            submitted = commandService.submitReservation(fixture.userId(), reservationRequest(
                    fixture,
                    StockOrderSide.BUY,
                    new BigDecimal("10"),
                    new BigDecimal("1000"),
                    new BigDecimal("1000")));
        }

        // 예약주문을 취소한다.
        commandService.cancelReservation(fixture.userId(), submitted.getId());

        // 이미 예약주문을 취소했기 때문에 또 예약주문을 취소하려고 하는 경우에는 예외가 발생한다.
        assertThatThrownBy(() -> commandService.cancelReservation(fixture.userId(), submitted.getId()))
                .hasMessage("활성 상태의 예약 주문만 취소할 수 있습니다.");
        // 취소된 예약주문을 검증한다.
        StockOrderReservation canceled = reservationRepository.findById(submitted.getId()).orElseThrow();
        InvestmentCashBalance cash = cashBalance(fixture);
        assertThat(canceled.getStatus()).isEqualTo(StockOrderReservationStatus.CANCELED);
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("0");
        // 예약주문이 취소되어 예약주문 -> 일반주문으로 접수되지 않았기 때문에 OrderRepository에 한건도 저장되면 안된다.
        assertThat(orderRepository.count()).isZero();
        // 실제로 주문이 체결되지 않았기 때문에 TransactionRepository에도 주문 원장이 한건도 저장되면 안된다.
        assertThat(transactionRepository.count()).isZero();
    }

    // 장을 항상 Open으로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                eq(StockMarketType.KOSPI), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
