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

class StockHoldingCreationConcurrencyTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradingExecutionService executionService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    // 매수주문이 두 스레드에서 동시에 실행되도 동시성 문제가 발생하지 않는지를 검사한다.
    @Test
    @DisplayName("ORD-013: 같은 계좌·종목의 최초 매수 체결이 동시에 실행돼도 보유 행은 하나이고 두 체결을 모두 반영한다")
    void concurrentFirstBuysCreateOneHoldingWithAllExecutions() throws Exception {
        // 테스트용 사용자, 증권계좌, 종목, 통화 개설한다. 이때 증권계좌에는 예수금 100000원을 입금한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));

        // realtimeService가 현재 매수기준 가격을 찾으려고 하면 Optional.empty()를 리턴한다.
        // 이는 주문을 생성할때 주문이 바로 체결되지 않도록 방지하고, 두 주문이 동시에 실행되게 하기 위해서 Optional.empty()로 설정한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());

        StockOrder firstOrder;
        StockOrder secondOrder;

        // try문안에 자원을 설정해두면 try문이 종료되면서 자원을 정리한다.
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 장이 항상 open되어 있다고 설정하기 위해서 StockMarketSchedules를 수정한다.

            // 첫번째 주식 주문 객체 생성 (지정가: 1000)
            firstOrder = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
            // 두번째 주식 주문 객체 생성
            secondOrder = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }

        // realtimeService가 현재 매수기준 가격을 찾으려고 하면 900을 리턴한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));

        // 두 스레드를 동시에 실행하게 만들기 위해 CyclicBarrier를 생성한다.
        CyclicBarrier start = new CyclicBarrier(2);
        // 두 스레드를 생성하기 위해 ExecutorService(스레드 풀)에 2개의 스레드를 생성한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 각각의 스레드에 두개의 주식 주문을 수행한다.
            Future<?> first = executor.submit(() -> executeOrderAfterBarrier(firstOrder.getId(), start));
            Future<?> second = executor.submit(() -> executeOrderAfterBarrier(secondOrder.getId(), start));

            // 각각의 스레드에서 결과가 나올때까지 최대 10초동안 대기하고, 결과를 반환받는다.
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            // 스레드 풀을 정리한다.
            executor.shutdownNow();
        }

        // 주문요청이 2개가 들어오더라도, StockHolding은 각 증권계좌의 특정 종목마다 한개씩만 관리되어야 한다.
        assertThat(holdingRepository.findByInvestment_Id(fixture.investmentId())).hasSize(1);
        // 두 주문이 체결되서 총 20개의 종목을 보유중인지 검사한다.
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("20");

        // 평균단가를 검사한다.
        // 지정가 주문은 매수기준 가격(900)이 지정가 가격(1000)보다 낮아지면 매수기준 가격으로 결제가 체결되기 떄문에 900원으로 주문이 체결된다.
        assertThat(holding(fixture).getAveragePurchasePrice()).isEqualByComparingTo("900.000000");
        // 증권계좌의 AvailableBalance와 LockedBalance를 검사한다.
        // 이때 거래수수료가 포함되기 때문에 AvailableBalance가 82000이 아니라 81998이 된다.
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("81998");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
        // TransactionRepository에 두 주문에 대한 원장이 기록되었는지 검사하고, 두 주문이 체결상태인지 검사한다.
        assertThat(transactionRepository.findByInvestment_IdOrderByExecutedAtDesc(fixture.investmentId())).hasSize(2);
        assertThat(orderRepository.findById(firstOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(StockOrderStatus.FILLED);
        assertThat(orderRepository.findById(secondOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(StockOrderStatus.FILLED);
    }

    private void executeOrderAfterBarrier(Long orderId, CyclicBarrier start) {
        try {
            // Barrier에 2개의 스레드가 도착할때까지 최대 5초동안 대기한다.
            start.await(5, TimeUnit.SECONDS);
            // 트랜잭션 내에서 StockOrder를
            transactionTemplate.executeWithoutResult(status -> {
                StockOrder order = orderRepository.findById(orderId).orElseThrow();
                try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                    // 장을 항상 open 상태로 설정한다.
                    // 장이 항상 open상태이기 때문에 주문요청이 성공되어야 하며, 주문이 접수되고 True가 리턴되는지를 검사한다.
                    assertThat(executionService.executeOrderIfPossible(order, false)).isTrue();
                }
            });
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static MockedStatic<StockMarketSchedules> openMarket() {
        // StockMarketSchedules 객체의 static 메서드를 수정하기 위해서 MockedStatic 객체로 감싼다.
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        // StockMarketSchedules의 isTradingTime메서드가 호출될 경우, MarketType이 KOSPI인 경우에는 항상 true만 리턴하도록 수정한다.
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                eq(StockMarketType.KOSPI), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
