package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import com.finmate.service.stock.trading.StockSettlementIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StockSettlementAtomicityIntegrationTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockOrderRepository orderRepository;
    // 스프링 컨테이너에서 빈을 주입받지만, 일부 기능을 수정해서 사용하기 위해서 MockitoSpyBean을 사용한다.
    @MockitoSpyBean
    private StockTradeTransactionRepository transactionRepository;

    @Test
    @DisplayName("ORD-009 LEDGER-001: 체결 원장 저장 실패는 주문·현금·보유수량 변경을 모두 rollback한다")
    void ledgerSaveFailureRollsBackOrderCashAndHolding() {
        // 테스트용 사용자, 증권계좌, 종목을 생성하고 예수금 100000원을 이체한다.
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // RealtimePriceService를 통해서 매수 기준 가격을 조회할때 매수기준 가격을 900원으로 리턴한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), eq(StockOrderSide.BUY)))
                .thenReturn(Optional.of(new BigDecimal("900")));
        // TransactionRepository에 주문 체결 원장을 저장하고자 할때 예외를 발생시킨다.
        doThrow(new RuntimeException("injected stock ledger failure"))
                .when(transactionRepository).save(any(StockTradeTransaction.class));

        try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
            // 주문을 접수하고, 바로 체결이 가능하면 (매수 기준가: 900원, 지정가: 1000원) 바로 주문을 체결한다.
            // 하지만 이때 주문 체결 원장을 저장할때 예외가 발생하기 때문에 주문 체결이 실패해야하며, 예외가 발생해야 한다.
            assertThatThrownBy(() -> commandService.submitOrder(fixture.userId(), orderRequest(
                    fixture, StockOrderSide.BUY, new BigDecimal("10"), new BigDecimal("1000"))))
                    .hasMessage("injected stock ledger failure");
        }

        InvestmentCashBalance cash = cashBalance(fixture);
        // 주문 원장 저장에 실패하게 되면 주문 접수 자체로 롤백이 되서 OrderRepository에 아무것도 저장이 되면 안된다.
        assertThat(orderRepository.count()).isZero();
        // 주문 체결 원장에 한건도 저장이 되어있으면 안된다.
        assertThat(jdbcTemplate.queryForObject("select count(*) from stock_trade_transaction", Long.class)).isZero();
        // 주문체결에 실패해야하기 때문에 보유 종목수량도 없어야 하며, 보유 예수금도 변화가 있으면 안된다.
        assertThat(holdingRepository.findByInvestment_IdAndStock_Id(
                fixture.investmentId(), fixture.stockId())).isEmpty();
        assertThat(cash.getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(cash.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(cash.getTotalBalance()).isEqualByComparingTo("100000");
    }

    // 장을 항상 open상태로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                any(Stock.class), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }
}
