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

// 예약주문 테스트
class StockOrderReservationTest {

    private static final LocalDateTime EXPIRATION = LocalDateTime.of(2099, 1, 5, 15, 30);

    @Test
    @DisplayName("ORD-003: 예약주문은 만료시각과 정확히 같을 때 만료된다")
    void reservationExpiresAtInclusiveBoundary() {
        StockOrderReservation reservation = reservation();
        assertThat(reservation.isExpired(EXPIRATION)).isTrue();
    }

    @Test
    @DisplayName("ORD-003: 예약주문은 만료시각 직전에는 만료되지 않는다")
    void reservationIsNotExpiredBeforeBoundary() {
        StockOrderReservation reservation = reservation();

        assertThat(reservation.isExpired(EXPIRATION.minusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("ORD-010: 활성 예약을 트리거하면 TRIGGERED로 종료하고 실행시각을 기록한다")
    void triggeringActiveReservationRecordsTerminalState() {
        StockOrderReservation reservation = reservation();
        LocalDateTime triggeredAt = LocalDateTime.of(2026, 7, 23, 9, 0);

        // 활성 예약주문이 예약 조건을 충족해서 실제 주문으로 전환되어 접수된다. -> 이제 예약주문은 종료되고 일반 주문으로 변환된다.
        reservation.markTriggered(triggeredAt);

        assertThat(reservation.getStatus()).isEqualTo(StockOrderReservationStatus.TRIGGERED);
        assertThat(reservation.getTriggeredAt()).isEqualTo(triggeredAt);
    }

    @Test
    @DisplayName("ORD-010: 종료된 예약에 두 번째 종료 전이를 적용할 수 없다")
    void rejectsSecondTerminalTransition() {
        StockOrderReservation reservation = reservation();
        reservation.cancel(); // 예약 종료

        // 이미 종료된 예약에 만료처리를 수행하면 예외가 발생한다.
        assertThatThrownBy(reservation::expire)
                .hasMessage("활성 예약만 만료 처리할 수 있습니다.");
        assertThat(reservation.getStatus()).isEqualTo(StockOrderReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("ORD-002: 활성이고 만료 전인 예약만 가격 조건을 평가한다")
    void activeUnexpiredReservationCanSatisfyTrigger() {
        StockOrderReservation reservation = reservation();

        // 예약이 활성상태이고, 만료 전인 경우에만 예약조건을 검사한다.
        assertThat(reservation.isTriggerSatisfied(new BigDecimal("900"))).isTrue();
    }

    @Test
    @DisplayName("ORD-010: 취소된 예약은 가격 조건을 만족해도 트리거되지 않는다")
    void canceledReservationCannotSatisfyTrigger() {
        StockOrderReservation reservation = reservation();
        reservation.cancel();

        // 예약이 이미 취소된 경우에 예약조건을 충족한 경우에도 예약주문이 트리거되지 않는다.
        assertThat(reservation.isTriggerSatisfied(new BigDecimal("900"))).isFalse();
    }

    // 예약주문 생성
    private static StockOrderReservation reservation() {
        return StockOrderReservation.create("reservation-001", investment(), stock(), StockOrderSide.BUY,
                StockOrderType.LIMIT, StockOrderTriggerCondition.PRICE_AT_OR_BELOW, CurrencyCode.KRW,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("950"),
                new BigDecimal("10000"), BigDecimal.ZERO, EXPIRATION);
    }

    // 증권계좌 생성
    private static Investment investment() {
        return Investment.create(new User(), "200-001", SecuritiesCompanyCode.KOREA_INVESTMENT);
    }

    // 종목정보 생성
    private static Stock stock() {
        return Stock.create("005930", "005930", "KR7005930003", "삼성전자", "Samsung",
                StockMarketType.KOSPI, "KR", "KRX", "KRW", StockSecurityType.COMMON_STOCK,
                false, null, LocalDateTime.of(2026, 7, 23, 0, 0));
    }
}
