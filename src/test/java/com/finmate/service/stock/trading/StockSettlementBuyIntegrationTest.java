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

class StockSettlementBuyIntegrationTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockOrderRepository orderRepository;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    @Test
    @DisplayName("ORD-006/007: 미체결 지정가 매수는 수수료 포함 예약액을 잠그고 total을 보존한다")
    void pendingBuyLocksReservedCashAndPreservesTotal() {
        // 테스트용 사용자, 증권계좌, 종목을 생성하고, 증권계좌에 예수금 100000원을 이체한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // RealtimePriceService에서 매수기준가격을 조회하면 Optional.empty()를 리턴한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.empty());

        StockOrder order;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 주문 생성 후 접수 처리(지정가: 1000원)
            // 이때 아직 매수기준가격이 empty이기 때문에 주문은 미체결상태로 남아있다.
            order = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }

        // 주문이 접수는 되어있지만, 미체결 상태일때 종목 구매를 위해  수수료 포함 예약액에 lock이 걸리는지를 검사하고 totalAmount는 유지되는지 검사한다.
        InvestmentCashBalance cash = cashBalance(fixture);
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.SUBMITTED);
        assertThat(order.getReservedCashAmount()).isEqualByComparingTo("10002"); // 예약액
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("89998");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("10002"); // 예약금액만큼 lock이 걸려야 한다.
        assertThat(cash.getTotalBalance()).isEqualByComparingTo("100000");
        assertThat(transactionRepository.count()).isZero(); // 미체결상태이기 때문에 아직 원장은 저장되지 않아야 한다.
    }

    @Test
    @DisplayName("ORD-007/014 LEDGER-001: 매수 체결은 예약 차액을 반환하고 평균가와 원장 snapshot을 기록한다")
    void buySettlementRefundsReservationDifferenceAndRecordsSnapshots() {
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 매수기준 가격: 900원
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));

        StockOrder submitted;
        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 주문 생성 (지정가: 1000원). 이때 지정가가 1000원이고 매수기준 가격이 900원이기떄문에 주문이 바로 체결된다.
            submitted = commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000")));
        }

        StockOrder order = orderRepository.findById(submitted.getId()).orElseThrow();
        InvestmentCashBalance cash = cashBalance(fixture);
        StockHolding holding = holding(fixture);
        List<StockTradeTransaction> ledgers = transactionRepository
                .findByOrder_IdOrderByExecutedAtDesc(order.getId());

        // 주문이 정상적으로 체결되어 LockedBalance에서 금액이 차감되었는지, 그리고 StockHolding에 보유 수량 및 평균 단가가 update되었는지를 검증한다.
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.FILLED);
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("90999");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(holding.getQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("900.000000");
        // 주문 원장에 매수 수량, 매수 금액, 매수 총금액, 세금, 수수료, 거래전 총액, 거래후 총액, 체결전 보유수량, 체결후 보유수량들이 정상적으로 저장되었는지 검사한다.
        assertThat(ledgers).singleElement().satisfies(ledger -> {
            assertThat(ledger.getSide()).isEqualTo(StockOrderSide.BUY);
            assertThat(ledger.getQuantity()).isEqualByComparingTo("10");
            assertThat(ledger.getExecutionPrice()).isEqualByComparingTo("900");
            assertThat(ledger.getGrossAmount()).isEqualByComparingTo("9000");
            assertThat(ledger.getCommissionAmount()).isEqualByComparingTo("1");
            assertThat(ledger.getTaxAmount()).isEqualByComparingTo("0");
            assertThat(ledger.getNetCashAmount()).isEqualByComparingTo("9001");
            assertThat(ledger.getCashBalanceBeforeTransaction()).isEqualByComparingTo("100000");
            assertThat(ledger.getCashBalanceAfterTransaction()).isEqualByComparingTo("90999");
            assertThat(ledger.getHoldingQuantityBeforeTransaction()).isEqualByComparingTo("0");
            assertThat(ledger.getHoldingQuantityAfterTransaction()).isEqualByComparingTo("10");
        });
    }

    // 장을 항상 open상태로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                eq(StockMarketType.KOSPI), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
