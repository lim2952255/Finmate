package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import com.finmate.service.stock.trading.StockSettlementIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

// 근데 StockHoldingCreationConcurrencyTest와 테스트가 매우 비슷한거같음
class StockRealtimePayloadConcurrencyTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradingExecutionService executionService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    @Test
    @DisplayName("ORD-011: 동일 실시간 payload를 동시에 처리해도 주문과 원장은 한 번만 체결된다")
    void concurrentDuplicatePayloadExecutesOrderExactlyOnce() throws Exception {
        // 테스트용 사용자, 증권계좌, 종목, 통화를 설정한다. 또한 예수금 100000원을 입금한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // RealtimePriceService에서 매수 기준 가격을 조회하고자 할때, Optional.empty()를 리턴한다.
        // 이는 주문을 생성할 때 주문이 바로 체결되지 않고, 미체결상태로 유지되게 하기 위해서 설정한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());
        StockOrder submitted;
        // 장을 항상 Open상태로 만든다.
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 주문을 접수한다. (지정가: 1000), 아직 해당 주문은 미체결상태로 남아있다.
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }
        // RealtimePriceService에서 매수 기준 가격을 조회하고자 할때, 900원을 리턴한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));

        // 두 스레드를 동시에 실행하기 위해서 CyclicBarrier객체를 생성한다.
        CyclicBarrier start = new CyclicBarrier(2);
        // 두 스레드를 생성하기 위해서 ExecutorService(스레드 풀)에 두개의 스레드를 생성한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 주문 하나를 두 스레드에서 동시에 수행하려고 할때, 주문이 중복으로 체결되지 않도록 테스트한다.
            Future<?> first = executor.submit(() -> processPayloadAfterBarrier(fixture.stockId(), start));
            Future<?> second = executor.submit(() -> processPayloadAfterBarrier(fixture.stockId(), start));

            // 두 스레드가 모두 종료될때까지 최대 10초동안 대기하고 결과를 받는다.
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        StockOrder order = orderRepository.findById(submitted.getId()).orElseThrow();
        // 주문이 체결되었는지와 주문이 하나만 체결되었는지를 검사한다.
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.FILLED);
        assertThat(order.getExecutedQuantity()).isEqualByComparingTo("10");
        assertThat(transactionRepository.findByOrder_IdOrderByExecutedAtDesc(submitted.getId())).hasSize(1);
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("90999");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("10");
    }

    private void processPayloadAfterBarrier(Long stockId, CyclicBarrier start) {
        try {
            // 두 스레드가 모두 준비가 될때까지 최대 5초동안 기다린다.
            start.await(5, TimeUnit.SECONDS);
            try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                // 지정가 주문을 매수기준 가격을 기반으로 주문을 체결할지 여부를 판단하고, 체결이 가능하면 주문을 체결한다.
                // 이때 ExecutionService의 processRealtimeUpdate 메서드는 해당 Stock에 설정되어있는 미체결 주문들을 조회한다.
                executionService.processRealtimeUpdate(stockId);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    // 장을 항상 open상태로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                any(Stock.class), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
