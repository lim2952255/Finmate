package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderStatus;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import com.finmate.service.stock.trading.StockSettlementIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StockSettlementSellIntegrationTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradingExecutionService executionService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    @Test
    @DisplayName("ORD-008/014 LEDGER-001: 매도 체결은 잠금수량을 소비하고 수수료·세금 차감 현금과 snapshot을 기록한다")
    void sellSettlementConsumesLockedQuantityAndRecordsNetCashSnapshots() {
        // 테스트 데이터 생성. 이때 예수금은 0원으로 설정한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, BigDecimal.ZERO);
        // StockHolding을 생성하며 종목 15개를 각각 평균단가 100원에 구매한다. 이때 증권계좌에서 예수금을 차감하지는 않는다.
        persistHolding(fixture, new BigDecimal("15"), new BigDecimal("100"));

        // 매도기준가격: 1000원
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.SELL)))
                .thenReturn(Optional.of(new BigDecimal("1000")));

        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 매도 주문 접수 및 체결(지정가: 1000원, 매도기준가격: 1000원) -> 따라서 주문이 바로 체결된다.(총 15개중 10개를 개당 1000원에 매도)
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.SELL, new BigDecimal("10"), new BigDecimal("1000")));
        }

        StockOrder order = orderRepository.findById(submitted.getId()).orElseThrow();
        InvestmentCashBalance cash = cashBalance(fixture);
        StockHolding holding = holding(fixture);
        List<StockTradeTransaction> ledgers = transactionRepository
                .findByOrder_IdOrderByExecutedAtDesc(order.getId());

        // 매도 주문이 체결되었는지, 매도 후 수량, 평균단가, 매도 정산가 (체결액 - 거래 수수료 - 거래세)를 검증한다.
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.FILLED);
        assertThat(holding.getQuantity()).isEqualByComparingTo("5");
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("0");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("100.000000");
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("9978");
        // 주문 원장을 검증한다.
        assertThat(ledgers).singleElement().satisfies(ledger -> {
            assertThat(ledger.getSide()).isEqualTo(StockOrderSide.SELL);
            assertThat(ledger.getGrossAmount()).isEqualByComparingTo("10000");
            assertThat(ledger.getCommissionAmount()).isEqualByComparingTo("2");
            assertThat(ledger.getTaxAmount()).isEqualByComparingTo("20");
            assertThat(ledger.getNetCashAmount()).isEqualByComparingTo("9978");
            assertThat(ledger.getCashBalanceBeforeTransaction()).isEqualByComparingTo("0");
            assertThat(ledger.getCashBalanceAfterTransaction()).isEqualByComparingTo("9978");
            assertThat(ledger.getHoldingQuantityBeforeTransaction()).isEqualByComparingTo("15"); // 거래전 보유 수량
            assertThat(ledger.getHoldingQuantityAfterTransaction()).isEqualByComparingTo("5"); // 거래 후 보유 수량
        });
    }

    @Test
    @DisplayName("ORD-009/010 LEDGER-002: 완료 주문을 다시 처리해도 원장과 자산 snapshot을 재작성하지 않는다")
    void completedOrderIsNotReprocessedAndLedgerRemainsImmutable() {
        // 테스트 데이터 생성. 이때 예수금은 0원으로 설ㅈ어한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, BigDecimal.ZERO);
        // 종목을 총 15개, 개당 100원에 보유중
        persistHolding(fixture, new BigDecimal("15"), new BigDecimal("100"));
        // 매도 기준가격: 1000원
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.SELL)))
                .thenReturn(Optional.of(new BigDecimal("1000")));

        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 주문을 접수하고, 바로 체결한다.(지정가: 1000원, 매도기준가격: 1000원)
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.SELL, new BigDecimal("10"), new BigDecimal("1000")));
            // 이미 주문이 체결되었기 때문에, executionService.processRealtimeUpdate를 호출하여도, 해당 종목에 대해서 남아있는 미체결 주문이 존재하지 않기 때문에 아무런 일도 일어나지 않는다.
            executionService.processRealtimeUpdate(fixture.stockId());
        }

        // 총 한건의 주문만 체결되어야 하며, 주문이 정상적으로 체결되었는지 검사한다.
        List<StockTradeTransaction> ledgers = transactionRepository
                .findByOrder_IdOrderByExecutedAtDesc(submitted.getId());
        assertThat(ledgers).hasSize(1);
        assertThat(ledgers.get(0).getCashBalanceBeforeTransaction()).isEqualByComparingTo("0");
        assertThat(ledgers.get(0).getCashBalanceAfterTransaction()).isEqualByComparingTo("9978");
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("5");
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("9978");
    }

    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                eq(StockMarketType.KOSPI), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
