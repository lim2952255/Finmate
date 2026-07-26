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

// 시장가 주문, 지정가 주문 테스트
// pendingLimitOrderIsNotExpired() 이 테스트가 약간 아쉬운듯? pending상태일때 만료시간이 지나면 주문이 접수되지않더라도 주문을 취소시켜야 되지 않나?
class StockOrderExpirationTest {
    // 주문 만료시간.
    private static final LocalDateTime EXPIRATION = LocalDateTime.of(2099, 1, 5, 15, 30);

    @Test
    @DisplayName("ORD-003: 시장가 일반주문은 전달된 만료시각을 저장하지 않는다")
    void marketOrderHasNoExpiration() {
        // 시장가 일반주문의 경우에는 만료시각을 지정하지 않는다.
        StockOrder order = order(StockOrderType.MARKET, null, EXPIRATION);
        assertThat(order.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문은 미래 만료시각을 저장한다")
    void limitOrderStoresFutureExpiration() {
        // 지정가 주문의 경우에는 만료시각을 지정해야 한다.
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);

        assertThat(order.getExpiresAt()).isEqualTo(EXPIRATION);
    }

    @Test
    @DisplayName("ORD-003: 제출된 지정가 주문은 만료시각과 정확히 같을 때 만료된다")
    void submittedLimitOrderExpiresAtInclusiveBoundary() {
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);
        order.markSubmitted(); // 접수대기중인 주문을 접수 (주문상태를 변경)

        // 만료시각와 현재시각이 같을때 지정가 주문이 만료된다.
        assertThat(order.isExpired(EXPIRATION)).isTrue();
    }

    @Test
    @DisplayName("ORD-003: 제출된 지정가 주문은 만료시각 직전에는 만료되지 않는다")
    void submittedLimitOrderIsNotExpiredBeforeBoundary() {
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);
        order.markSubmitted();

        // 지정가 주문은 만료시각 직전까지는 만료되지 않는다.
        assertThat(order.isExpired(EXPIRATION.minusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("ORD-003: PENDING 지정가 주문은 만료시각이 지나도 만료 대상으로 판정하지 않는다")
    void pendingLimitOrderIsNotExpired() {
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);

        assertThat(order.isExpired(EXPIRATION.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("ORD-003: 만료는 남은 수량을 취소수량으로 옮기고 EXPIRED로 종료한다")
    void expirationCancelsRemainingQuantity() {
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);
        order.markSubmitted();
        // 10개중 4개만 부분체결
        order.applyExecution(new BigDecimal("4"));

        // 주문 만료
        order.expireRemaining();

        // 주문 만료시에 부분체결 수량과 취소 수량을 계산하고 검증한다. 또한 주문 만료시에는 RemainingQuantity가 0이어야 한다.
        assertThat(order.getExecutedQuantity()).isEqualByComparingTo("4");
        assertThat(order.getCanceledQuantity()).isEqualByComparingTo("6");
        assertThat(order.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(StockOrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("ORD-003: 활성 상태가 아닌 주문의 만료 전이를 거부한다")
    void rejectsExpirationForPendingOrder() {
        StockOrder order = order(StockOrderType.LIMIT, new BigDecimal("1000"), EXPIRATION);

        // 주문 만료는 활성상태(Submitted)상태에서만 처리한다.
        assertThatThrownBy(order::expireRemaining)
                .hasMessage("접수 중인 주문만 만료 처리할 수 있습니다.");
    }

    // 종목 주문 생성
    private static StockOrder order(StockOrderType type, BigDecimal price, LocalDateTime expiresAt) {
        return StockOrder.create("order-001", investment(), stock(), null, StockOrderSide.BUY, type,
                CurrencyCode.KRW, new BigDecimal("10"), price, new BigDecimal("10000"),
                BigDecimal.ZERO, expiresAt);
    }
    // 증권계좌 개설
    private static Investment investment() {
        return Investment.create(new User(), "200-001", SecuritiesCompanyCode.KOREA_INVESTMENT);
    }
    // 삼성전자 종목 개설
    private static Stock stock() {
        return Stock.create("005930", "005930", "KR7005930003", "삼성전자", "Samsung",
                StockMarketType.KOSPI, "KR", "KRX", "KRW", StockSecurityType.COMMON_STOCK,
                false, null, LocalDateTime.of(2026, 7, 23, 0, 0));
    }
}
