package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingAmountValidatorTest {

    @DisplayName("ORD-006: 거래 수량은 최대 6자리 scale로 정규화한다")
    @ParameterizedTest(name = "quantity={0}")
    @MethodSource("validQuantities")
    void normalizesQuantityUpToSixDecimals(String input, String expected) {
        // 종목의 거래 수량 scale은 6자리 scale로 정규화한다. 1 -> 1.000000
        assertThat(TradingAmountValidator.normalizeRequiredQuantity(new BigDecimal(input)))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("ORD-006: 거래 수량 scale이 6자리를 초과하면 거부한다")
    void rejectsQuantityBeyondSixDecimals() {
        // 거래 수량의 scale이 6자리를 초과하면 예외가 발생한다.
        assertThatThrownBy(() -> TradingAmountValidator.normalizeRequiredQuantity(
                new BigDecimal("0.0000001")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("ORD-003: 시장가 일반주문은 만료시각이 없어도 허용한다")
    void allowsMarketOrderWithoutExpiration() {
        TradingAmountValidator.validateOrderExpiration(StockOrderType.MARKET, null);
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문은 만료시각이 필수다")
    void rejectsLimitOrderWithoutExpiration() {
        assertThatThrownBy(() -> TradingAmountValidator.validateOrderExpiration(StockOrderType.LIMIT, null))
                .hasMessage("지정가 주문 만료시각은 필수입니다.");
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문의 과거 만료시각을 거부한다")
    void rejectsPastExpirationForLimitOrder() {
        // 지정가 주문의 만료시각이 현재시각보다 이전이라면 예외가 발생해야 한다.
        assertThatThrownBy(() -> TradingAmountValidator.validateOrderExpiration(
                StockOrderType.LIMIT, LocalDateTime.now().minusDays(1)))
                .hasMessage("지정가 주문 만료시각은 현재 시각 이후여야 합니다.");
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문의 미래 만료시각을 허용한다")
    void allowsFutureExpirationForLimitOrder() {
        // 지정가 주문의 만료시각이 현재시각보다 미래라면 정상적으로 처리되어야 한다.
        TradingAmountValidator.validateOrderExpiration(
                StockOrderType.LIMIT, LocalDateTime.now().plusDays(1));
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문은 접수 시각으로부터 최소 5분 이후에 만료되어야 한다")
    void rejectsLimitOrderExpirationBeforeMinimumLeadTime() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

        // 최소 주문 만료 기한은 현재시간 + 5분이므로 예외가 발생해야 한다.
        assertThatThrownBy(() -> TradingAmountValidator.validateOrderSubmissionExpiration(
                StockOrderType.LIMIT, now.plusMinutes(5).minusNanos(1), now))
                .hasMessage("주문 만료시각은 접수 시각으로부터 최소 5분 이후여야 합니다.");
    }

    @Test
    @DisplayName("ORD-003: 지정가 일반주문은 접수 시각으로부터 정확히 5분 후 만료를 허용한다")
    void allowsLimitOrderAtMinimumLeadTimeBoundary() {
        // 주문 만료기한이 정확히 5분뒤인 주문도 접수 처리한다.
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);


        TradingAmountValidator.validateOrderSubmissionExpiration(
                StockOrderType.LIMIT, now.plusMinutes(5), now);
    }

    @Test
    @DisplayName("ORD-003: 주문 만료시각은 접수 시각으로부터 최대 30일 이내여야 한다")
    void rejectsExpirationBeyondMaximumLeadTime() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

        // 주문 만료시각의 최대기한은 30일까지이므로 이를 초과하면 예외가 발생한다.
        assertThatThrownBy(() -> TradingAmountValidator.validateReservationSubmissionExpiration(
                now.plusDays(30).plusNanos(1), now))
                .hasMessage("주문 만료시각은 접수 시각으로부터 최대 30일 이내여야 합니다.");
    }
    // >
    @Test
    @DisplayName("ORD-003: 예약주문은 접수 시각으로부터 정확히 30일 후 만료를 허용한다")
    void allowsReservationAtMaximumLeadTimeBoundary() {
        // 예약주문 만료기한이 정확이 30일 뒤인 주문은 접수한다.
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 12, 0);

        TradingAmountValidator.validateReservationSubmissionExpiration(now.plusDays(30), now);
    }

    @Test
    @DisplayName("ORD-007: 거래대금 CEILING은 통화 최소 단위로 올림한다")
    void calculatesGrossAmountUsingCurrencyScaleAndCeiling() {
        // 가격 * 수량을 통해 계산한 거래대금을 통화의 scale 정책에 맞게 정규화되는지를 검증한다.
        BigDecimal result = TradingAmountValidator.calculateAmount(
                CurrencyCode.USD, new BigDecimal("10.001"), new BigDecimal("1"), RoundingMode.CEILING);

        assertThat(result).isEqualByComparingTo("10.01"); // 달러는 scale을 2까지 허용한다.
    }

    @Test
    @DisplayName("ORD-014: 거래대금 HALF_UP은 통화 최소 단위로 반올림한다")
    void calculatesAmountUsingCurrencyScaleAndHalfUp() {
        // 가격 * 수량을 통해 계산한 거래대금을 통화의 scale 정책에 맞게 정규화되는지를 검증한다.

        BigDecimal result = TradingAmountValidator.calculateAmount(
                CurrencyCode.KRW, new BigDecimal("100.5"), new BigDecimal("1"), RoundingMode.HALF_UP);

        assertThat(result).isEqualByComparingTo("101"); // 원화는 scale을 0까지 허용한다.
    }

    // Test에 파라미터로 넘겨줄 인자(Arguments)들을 Stream에 담아서 반환한다.
    private static Stream<Arguments> validQuantities() {
        return Stream.of(
                Arguments.of("1", "1.000000"),
                Arguments.of("0.000001", "0.000001"),
                Arguments.of("1.234567", "1.234567")
        );
    }
}
