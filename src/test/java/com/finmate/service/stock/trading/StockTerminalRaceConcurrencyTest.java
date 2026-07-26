package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StockTerminalRaceConcurrencyTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradingExecutionService executionService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockOrderReservationRepository reservationRepository;

    // 스프링 컨테이너에서 빈을 주입받지만, 일부 기능을 수정하기 위해서 MockitoSpyBean을 사용한다.
    @MockitoSpyBean
    private StockTradingLookupService lookupService;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    /*
    * 1. 취소 작업이 주문을 읽은 뒤 잠깐 멈춤
    * 2. 그사이에 체결 작업이 먼저 주문을 FILLED로 만듦
    * 3. 취소 작업을 다시 진행
    * 4. 이미 체결된 주문이므로 취소는 실패
    * */
    @Test
    @DisplayName("ORD-010: 취소가 활성 주문을 읽은 뒤 체결과 경합해도 체결만 자산과 원장을 한 번 변경한다")
    void cancelVersusExecutionHasOneTerminalEffect() throws Exception {
        // 테스트 데이터 생성
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 매수 기준가격을 empty로 설정
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());
        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 매수주문 생성 -> 아직 미체결상태
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }

        // readLatch와 ContinueLatch의 count를 1로 생성한다. (스레드 실행순서를 조절하기 위함)
        CountDownLatch cancelReadActive = new CountDownLatch(1); // 취소 스레드를 멈추는 Latch
        CountDownLatch allowCancelToContinue = new CountDownLatch(1); // 취소 스레드를 다시 이동시키는 Latch
        // lookupService의 validateOwnedInvestment 메서드를 재구성한다. (메서드 호출후 readLatch를 1만큼 감소시키고, continueLatch 대기)
        blockCancelAfterRead(fixture.userId(), cancelReadActive, allowCancelToContinue);
        // 매수기준 가격을 900원으로 설정한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));

        // 스레드풀에 스레드를 2개를 생성한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 주문 취소작업을 먼저 실행한다.
            // 이때 commandService.cancelOrder 메서드 내에서 LookupService의 validateOwnedInvestment 메서드를 호출해서 증권계좌가 사용자 명의가 맞는지를 검사한다.
            // 그리고 이때 readLatch를 1만큼 감소시켜 0이되며, continueLatch에서 대기한다 -> 따라서 아직 주문이 취소되지는 않는다.

            // Future<Throwable>은 작업을 수행하면서 발생한 예외를 담는다.(만약 예외가 발생하지 않으면 Null이 담긴다.)
            Future<Throwable> cancel = executor.submit(() -> captureFailure(() ->
                    commandService.cancelOrder(fixture.userId(), submitted.getId())));
            // readLatch가 0이 될때까지 최대 5초간 대기한다. 이때 주문 취소 스레드가 commandService.cancelOrder를 호출하면 readLatch가 0이된다.
            assertThat(cancelReadActive.await(5, TimeUnit.SECONDS)).isTrue();
            // 해당 종목에 남아있는 미체결 주문들의 체결가능 여부를 파악한 후 주문을 체결한다.(지정가: 1000원, 매수기준가: 900원 -> 체결가능)
            // 이때 아직 주문이 취소되지않고 대기중이기 때문에 주문 체결이 가능하며, 주문을 체결한다.
            Future<Throwable> execute = executor.submit(() -> captureFailure(() -> {
                try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                    executionService.processRealtimeUpdate(fixture.stockId());
                }
            }));
            // 주문 체결작업은 정상적으로 실행되었기 때문에 예외가 발생하지 않는다.
            assertThat(execute.get(10, TimeUnit.SECONDS)).isNull();
            allowCancelToContinue.countDown(); // ContinueLatch의 count를 감소시킨다 -> 주문 취소 스레드가 다시 실행된다.
            // 이미 주문이 체결되었기 때문에 주문은 취소되지 않고 예외가 발생한다.
            assertThat(cancel.get(10, TimeUnit.SECONDS)).isNotNull();
        } finally {
            allowCancelToContinue.countDown(); // 예외발생 시에도 주문취소 스레드가 다시 실행될 수 있도록 ContinueLatch의 count를 감소시킨다.
            executor.shutdownNow();
        }

        // 최종적으로 주문 체결 작업은 성공하고, 주문 취소 작업은 실패해야 한다.
        StockOrder terminal = orderRepository.findById(submitted.getId()).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(StockOrderStatus.FILLED);
        assertThat(transactionRepository.findByOrder_IdOrderByExecutedAtDesc(submitted.getId())).hasSize(1);
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("90999");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("ORD-010: 취소가 활성 주문을 읽은 뒤 만료와 경합해도 만료만 잠금액을 한 번 반환한다")
    void cancelVersusExpirationHasOneTerminalEffect() throws Exception {
        // 테스트 데이터 생성 및 예수금 100000원 입금
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 매수기준 가격을 empty로 설정
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());
        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 매수 주문을 접수한다 (수량: 10개, 가격: 1000원) -> 하지만 아직 매수기준 가격이 empty이기 때문에 미체결상태이다.
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }
        // 주문 만료기한을 현재시간보다 빠르게 설정한다 -> 주문이 만료처리된다.
        jdbcTemplate.update("update stock_order set expires_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), submitted.getId());

        // readLatch와 ContinueLatch의 count를 1로 생성한다. (스레드 실행순서를 조절하기 위함)
        CountDownLatch cancelReadActive = new CountDownLatch(1);
        CountDownLatch allowCancelToContinue = new CountDownLatch(1);
        // lookupService의 validateOwnedInvestment 메서드를 재구성한다. (메서드 호출후 readLatch를 1만큼 감소시키고, continueLatch 대기)
        blockCancelAfterRead(fixture.userId(), cancelReadActive, allowCancelToContinue);

        // 스레드 풀에 스레드 2개 생성
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 주문 취소 스레드 실행( commandService.cancelOrder에서 continueLatch를 대기한다)
            Future<Throwable> cancel = executor.submit(() -> captureFailure(() ->
                    commandService.cancelOrder(fixture.userId(), submitted.getId())));
            assertThat(cancelReadActive.await(5, TimeUnit.SECONDS)).isTrue(); // commandService.cancelOrder가 호출되면 readLatch가 0이 되며 실행가능 상태가 된다.

            // 주문 만료처리를 수행한다.
            Future<Throwable> expire = executor.submit(() -> captureFailure(() ->
                    executionService.processRealtimeUpdate(fixture.stockId())));

            // 이때 주문 만료처리는 성공하기 때문에 예외가 발생하지 않는다.
            assertThat(expire.get(10, TimeUnit.SECONDS)).isNull();
            allowCancelToContinue.countDown(); // ContinueLetch의 count를 감소시킴으로서 주문 취소 스레드가 작업을 재개한다.
            // 이미 주문이 만료되었기 때문에, 주문 취소 작업은 실패하며 예외가 발생한다.
            assertThat(cancel.get(10, TimeUnit.SECONDS)).isNotNull();
        } finally {
            allowCancelToContinue.countDown();
            executor.shutdownNow();
        }

        // 주문이 만료되었ㄴ느지를 검사한다.
        StockOrder terminal = orderRepository.findById(submitted.getId()).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(StockOrderStatus.EXPIRED);
        // 주문이 만료되었기 때문에 TrasactionRepository에 원장이 기록되면 안된다.
        assertThat(transactionRepository.findByOrder_IdOrderByExecutedAtDesc(submitted.getId())).isEmpty();
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-010: 예약 취소가 활성 예약을 읽은 뒤 트리거와 경합해도 트리거·체결만 한 번 반영된다")
    void reservationCancelVersusTriggerHasOneTerminalEffect() throws Exception {
        // 테스트 데이터 생성 및 예수금 100000원 이체
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        StockOrderReservation submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 예약 주문 생성 및 접수 (매수 수량: 10개, 예약기준 가격: 1000원, 매수 지정가: 1000원)
            submitted = commandService.submitReservation(fixture.userId(), reservationRequest(
                    fixture,
                    StockOrderSide.BUY,
                    new BigDecimal("10"),
                    new BigDecimal("1000"),
                    new BigDecimal("1000")));
        }

        // readLatch와 ContinueLatch의 count를 1로 생성한다. (스레드 실행순서를 조절하기 위함)
        CountDownLatch cancelReadActive = new CountDownLatch(1);
        CountDownLatch allowCancelToContinue = new CountDownLatch(1);
        // lookupService의 validateOwnedInvestment 메서드를 재구성한다. (메서드 호출후 readLatch를 1만큼 감소시키고, continueLatch 대기)
        blockCancelAfterRead(fixture.userId(), cancelReadActive, allowCancelToContinue);

        // 매수 기준가격을 900원으로 설정
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));

        // 스레드 풀에 스레드 2개 생성
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 예약주문 취소 스레드를 실행한다. (commandService.cancelReservation에서 continueLatch를 대기한다)
            Future<Throwable> cancel = executor.submit(() -> captureFailure(() ->
                    commandService.cancelReservation(fixture.userId(), submitted.getId())));
            assertThat(cancelReadActive.await(5, TimeUnit.SECONDS)).isTrue(); // commandService.cancelReservation이 readLatch가 0이 되며 실행가능 상태가 된다.

            // 예약 주문의 trigger조건을 확인 후 예약조건을 충족하면 예약주문을 일반 주문으로 접수하고, 일반 주문 체결이 가능하면 일반주문을 체결한다.
            Future<Throwable> trigger = executor.submit(() -> captureFailure(() -> {
                try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                    executionService.processRealtimeUpdate(fixture.stockId());
                }
            }));

            // 예약조건 trigger 작업은 성공하기 때문에 예외가 발생하지 않는다.
            assertThat(trigger.get(10, TimeUnit.SECONDS)).isNull();
            allowCancelToContinue.countDown();
            // 이미 예약 주문이 체결되었기 때문에 예약취소 작업은 실패하고 예외가 발생한다.
            assertThat(cancel.get(10, TimeUnit.SECONDS)).isNotNull();
        } finally {
            allowCancelToContinue.countDown();
            executor.shutdownNow();
        }

        // 예약주문이 Triggere되고, 일반 주문으로 변환되어 체결되었는지를 검증한다.
        StockOrderReservation terminal = reservationRepository.findById(submitted.getId()).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(StockOrderReservationStatus.TRIGGERED);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("90999");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("10");
    }

    // 주문 취소 스레드 validateOwnedInvestment까지 실행한 후, 잠시 멈추게 하는 메서드
    private void blockCancelAfterRead(Long userId,
                                      CountDownLatch readLatch,
                                      CountDownLatch continueLatch) {
        // lookupService는 MockSpyBean이며, lookupService의 validateOwnedInvestment 메서드를 수정한다.
        doAnswer(invocation -> {
            // lookupService의 실제 validateOwnedInvestment 메서드를 호출한다.
            // 이 메서드는 증권계좌가 본인 명의의 계좌인지를 검사한다.
            Object result = invocation.callRealMethod();
            // 만약 현재 스레드가 스레드풀에서 실행되고 있는 작업스레드인 경우
            if (Thread.currentThread().getName().contains("pool-")) {
                // readLatch 카운트를 1만큼 감소시킨다.
                readLatch.countDown(); // 이때 readLatch의 count가 0이 되면 readLatch.await하고 있던 다른 스레드가 작업을 재개한다.

                // continueLatch의 count가 0이 될때까지 최대 10초동안 대기한다.
                if (!continueLatch.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("cancel worker was not released");
                }
            }
            return result;
        }).when(lookupService).validateOwnedInvestment(eq(userId), any(Investment.class));
    }

    // 전달받은 작업을 수행하고, 발생한 예외를 반환한다.
    private static Throwable captureFailure(ThrowingRunnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    // 장을 항상 open상태로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                eq(StockMarketType.KOSPI), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }

    // 예외를 던질 수 있는 작업을 사용자 정의 함수형 인터페이스로 구현
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
