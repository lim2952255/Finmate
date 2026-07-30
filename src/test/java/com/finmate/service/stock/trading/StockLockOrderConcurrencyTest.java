package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
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

// 이때 FinancialIntegrationTestSupport를 상속받기 떄문에, 스프링 컨텍스트를 로드하고, 테스트용 DB에 연결해서 테스트용 사용자와 계좌를 등록한다.
/*
 * 기존 구현의 문제점:
 * 매수 주문은 Cash -> Holding 순서로 락을 획득했지만, 매도 주문은 Holding -> Cash 순서로 획득했다.
 * 주문 접수와 즉시 체결이 같은 트랜잭션에서 이어지므로 서로 반대 순서의 락을 보유하면 순환 대기가 발생할 수 있다.
 *
 * 여러 주문이 동시에 접수되면 동일한 예수금이나 보유 수량을 읽고 예약하면서
 * lockedBalance 또는 lockedQuantity가 꼬일 수 있으므로 주문 접수 시점의 비관적 락은 필요하다.
 * 현재 구조에서는 매수와 매도 모두 Cash -> Holding 순서로 통일해 데드락 가능성을 제거한다.
 *
 * 장기적으로 주문 접수와 체결의 트랜잭션을 분리할 수 있지만, 별도의 일관성 설계가 필요한 변경이므로
 * 이 테스트에서는 현재의 단일 트랜잭션 구조와 통일된 락 순서를 검증한다.
 *
 * 기존 테스트처럼 두 작업이 서로 다른 락을 먼저 잡도록 강제하면 테스트 자체가 데드락을 만들어 낸다.
 * 실제 공개 주문 흐름에서 매수와 매도를 동시에 시작하고 둘 다 제한 시간 안에 완료되는지 검증해야 한다.
 */

class StockLockOrderConcurrencyTest extends StockSettlementIntegrationTestSupport {

    @Autowired
    private StockTradingCommandService commandService;

    @Autowired
    private StockTradeTransactionRepository transactionRepository;

    /*
     * 1. 같은 투자계좌와 종목에 대한 매수 주문과 매도 주문을 barrier에서 동시에 시작한다.
     * 2. 두 주문 모두 접수와 즉시 체결 과정에서 예수금(Cash) 후 보유 수량(Holding) 순서로 락을 획득한다. 따라서 매수주문과 매도주문을 동시에 실행해도 데드락이 발생하지 않는다.
     * 3. 두 작업이 timeout 안에 완료되고 예외가 없어야 한다.
     * 4. 최종 예수금·보유수량·잠금수량과 체결 원장이 두 주문의 결과를 모두 반영해야 한다.
     *
     * 같은 투자계좌의 command 경로는 Investment 락도 먼저 획득하므로 요청 전체가 직렬화될 수 있다.
     * 이 테스트는 실제 공개 주문 접수 흐름에서 동시 매수·매도가 데드락 없이 완료되는지를 검증한다.
     */
    @Test
    @DisplayName("ORD-012: 동시 매수·매도 주문은 Cash→Holding 락 순서로 데드락 없이 체결된다")
    void simultaneousBuyAndSellOrdersDoNotDeadlock() throws Exception {
        // 테스트용 데이터 생성 및 예수금 100000원 이체
        Fixture fixture = persistFixture(CurrencyCode.KRW, new BigDecimal("100000"));
        // 보유 종목 수량을 10개, 평균단가를 100원으로 설정한다.
        persistHolding(fixture, new BigDecimal("10"), new BigDecimal("100"));
        // 매수는 900원, 매도는 1000원에 즉시 체결되도록 각 주문 방향별 실행 가격을 설정한다.
        when(realtimePriceService.findExecutablePrice(any(Stock.class), any(StockOrderSide.class)))
                .thenAnswer(invocation -> {
                    StockOrderSide side = invocation.getArgument(1);
                    return Optional.of(side == StockOrderSide.BUY
                            ? new BigDecimal("900")
                            : new BigDecimal("1000"));
                });

        // 두 주문 요청이 모두 준비된 뒤 동시에 commandService에 진입하도록 barrier를 설정한다.
        CyclicBarrier start = new CyclicBarrier(2);
        // 매수 스레드 생성
        ExecutorService buyExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "buy-path"));
        // 매도 스레드 생성
        ExecutorService sellExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "sell-path"));
        Throwable buyFailure;
        Throwable sellFailure;
        try {
            Future<Throwable> buy = buyExecutor.submit(() -> captureFailure(() -> {
                start.await(5, TimeUnit.SECONDS); // 매수 스레드와 매도 스레드가 동시에 실행되도록 대기한다.
                try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                    // 매수 주문은 접수와 즉시 체결에서 Cash -> Holding 순서로 락을 획득한다.
                    commandService.submitOrder(fixture.userId(), orderRequest(
                            fixture, StockOrderSide.BUY, new BigDecimal("5"), new BigDecimal("1000")));
                }
            }));
            Future<Throwable> sell = sellExecutor.submit(() -> captureFailure(() -> {
                start.await(5, TimeUnit.SECONDS); // 매수 스레드와 매도 스레드가 동시에 실행되도록 대기한다.
                try (MockedStatic<StockMarketSchedules> schedules = openMarket()) {
                    // 매도 주문도 접수와 즉시 체결에서 Cash -> Holding 순서로 락을 획득한다.
                    commandService.submitOrder(fixture.userId(), orderRequest(
                            fixture, StockOrderSide.SELL, new BigDecimal("5"), new BigDecimal("1000")));
                }
            }));

            // timeout은 데이터베이스 데드락이나 무한 대기를 테스트 실패로 드러낸다.
            buyFailure = buy.get(10, TimeUnit.SECONDS);
            sellFailure = sell.get(10, TimeUnit.SECONDS);
        } finally {
            buyExecutor.shutdownNow();
            sellExecutor.shutdownNow();
        }

        // 매수 작업과 매도 작업이 모두 성공해야한다.
        assertThat(buyFailure).isNull();
        assertThat(sellFailure).isNull();
        // 원장이 2건이 저장되어야 한다.
        // 또한 매수주문과 매도주문이 모두 체결되었는지를 검증한다.
        assertThat(transactionRepository.findByInvestment_IdOrderByExecutedAtDesc(fixture.investmentId())).hasSize(2);
        assertThat(holding(fixture).getQuantity()).isEqualByComparingTo("10");
        assertThat(holding(fixture).getLockedQuantity()).isEqualByComparingTo("0");
        assertThat(cashBalance(fixture).getAvailableBalance()).isEqualByComparingTo("100488");
        assertThat(cashBalance(fixture).getLockedBalance()).isEqualByComparingTo("0");
    }

    // 작업을 실행하고, 발생한 예외를 리턴한다.
    private static Throwable captureFailure(ThrowingRunnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    // 장을 open상태로 만든다.
    private static MockedStatic<StockMarketSchedules> openMarket() {
        MockedStatic<StockMarketSchedules> schedules = mockStatic(StockMarketSchedules.class);
        schedules.when(() -> StockMarketSchedules.isTradingTime(
                any(Stock.class), any(ZonedDateTime.class))).thenReturn(true);
        return schedules;
    }

    // 예외를 던질 수 있는 작업을 함수형 인터페이스로 구현한다.
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
