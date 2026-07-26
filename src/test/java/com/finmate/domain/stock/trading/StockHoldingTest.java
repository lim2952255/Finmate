package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.StockSecurityType;
import com.finmate.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 보유중인 종목 수량에 대한 lock이 제대로 동작하는지를 검사한다.
class StockHoldingTest {

    @Test
    @DisplayName("ORD-006: 보유수량과 정확히 같은 수량을 잠글 수 있다")
    void locksEntireAvailableQuantity() {
        // 주식 10주를 각각 100원에 구매
        StockHolding holding = holdingWithQuantity("10", "100");
        // 보유중인 주식 10주에 lock을 건다.
        holding.lockQuantity(new BigDecimal("10"));

        // 보유 주식에 lock이 정상적으로 걸렸는지 검증한다.
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getAvailableQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-006: 매도 가능 수량을 초과한 잠금은 수량을 변경하지 않는다")
    void rejectsLockBeyondAvailableQuantityWithoutChangingQuantity() {
        // 주식 10주를 각각 100원에 구매
        StockHolding holding = holdingWithQuantity("10", "100");
        // 주식 10주 중 4주만 lock을 건다.
        holding.lockQuantity(new BigDecimal("4"));

        // 현재 사용가능한 수랑(6주)보다 많은 수량에 대해 lock을 걸려고 하면 예외가 발생해야 한다.
        assertThatThrownBy(() -> holding.lockQuantity(new BigDecimal("6.000001")))
                .hasMessage("매도 가능 수량이 부족합니다.");
        assertThat(holding.getQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("ORD-008: 매도 체결은 quantity와 lockedQuantity를 같은 수량만큼 줄인다")
    void sellExecutionReducesQuantityAndLockBySameAmount() {
        // 주식 10주를 각각 100원에 구매
        StockHolding holding = holdingWithQuantity("10", "100");
        holding.lockQuantity(new BigDecimal("6"));

        // 총 4주만큼 매도한다.
        holding.applySellExecution(new BigDecimal("4"));

        // availableQuentity는 변하지 않고, LockedQuentity에서 매도 수량만큼을 차감한다.
        assertThat(holding.getQuantity()).isEqualByComparingTo("6");
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("2");
        assertThat(holding.getAvailableQuantity()).isEqualByComparingTo("4");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("ORD-008: 전량 매도 체결은 평균 매수가를 0으로 초기화한다")
    void fullSellExecutionResetsAveragePurchasePrice() {
        StockHolding holding = holdingWithQuantity("10", "100");
        holding.lockQuantity(new BigDecimal("10"));

        // 전량 매도 체결
        holding.applySellExecution(new BigDecimal("10"));

        // 전량 매도 체결시 해당 증권계좌의 해당 종목에 대한 평균 매수가는 0이 되어야 한다.
        assertThat(holding.getQuantity()).isEqualByComparingTo("0");
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("0");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-014: 매수 체결의 가중 평균 매수가는 6자리 HALF_UP으로 계산한다")
    void buyExecutionRoundsWeightedAveragePriceHalfUpToSixDecimals() {
        StockHolding holding = holdingWithQuantity("1", "1");
        // 주식을 2주만큼 추가 매수
        holding.applyBuyExecution(new BigDecimal("2"), new BigDecimal("2"));

        // 종목 평균 매수가는 소수점 6번째자리까지 반올림한다.
        assertThat(holding.getQuantity()).isEqualByComparingTo("3");
        assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("1.666667");
    }

    @Test
    @DisplayName("ORD-006: 잠금 수량을 전부 해제하면 전체 보유수량이 다시 매도 가능해진다")
    void releasingEntireLockRestoresAvailableQuantity() {
        StockHolding holding = holdingWithQuantity("10", "100");
        holding.lockQuantity(new BigDecimal("4"));

        // 잠금 수량을 전부 해제
        holding.releaseLockedQuantity(new BigDecimal("4"));

        // 잠금 수량 전부 해제한 경우
        assertThat(holding.getLockedQuantity()).isEqualByComparingTo("0");
        assertThat(holding.getAvailableQuantity()).isEqualByComparingTo("10");
    }

    private static StockHolding holdingWithQuantity(String quantity, String price) {
        Investment investment = Investment.create(new User(), "200-001",
                SecuritiesCompanyCode.KOREA_INVESTMENT); // 계좌 개설
        // 종목정보 생성
        Stock stock = Stock.create("005930", "005930", "KR7005930003", "삼성전자", "Samsung",
                StockMarketType.KOSPI, "KR", "KRX", "KRW", StockSecurityType.COMMON_STOCK,
                false, null, LocalDateTime.of(2026, 7, 23, 0, 0));
        // 특정 증권계좌가 특정 종목을 얼마나 가지고 있는지를 담은 StockHolding 객체 생성
        StockHolding holding = StockHolding.create(investment, stock, CurrencyCode.KRW);
        holding.applyBuyExecution(new BigDecimal(quantity), new BigDecimal(price));
        return holding;
    }
}
