package com.finmate.domain.investment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CurrencyCode enum이 통화별 금액 규칙을 제대로 제공하고 검증하는지 확인하는 단위 테스트
// CurrencyCode에 통화별 규칙이 제대로 설정되어 있는지를 검증한다.
class CurrencyCodeTest {

    @DisplayName("INV-006: 통화별 최소 금액과 입력 단위를 제공한다")
    @ParameterizedTest(name = "{0} minimum={1}") // 테스트에 파라미터를 제공하는 에노테이션
    @MethodSource("currencyMinimums") // 테스트 파라미터에 데이터를 제공하는 에노테이션 -> 지정한 메서드를 통해서 파라미터를 입력받는다.
    void providesMinimumAmountAndInputStep(CurrencyCode currencyCode, String expected) {
        // 통화별 최소 금액과 최소 입력 단위 검증 (원화: 1원, 달러: 0.01)
        assertThat(currencyCode.getMinimumAmount()).isEqualByComparingTo(expected);
        assertThat(currencyCode.getInputStep()).isEqualByComparingTo(expected);
    }

    @DisplayName("INV-006: 통화별 허용 소수 자릿수의 금액을 허용한다")
    @ParameterizedTest(name = "{0} amount={1}")
    @MethodSource("validAmounts")
    void acceptsAmountWithinCurrencyScale(CurrencyCode currencyCode, String amount) {
        currencyCode.validateAmountScale(new BigDecimal(amount));
    }

    @DisplayName("INV-006: 통화별 허용 소수 자릿수를 초과한 금액을 거부한다")
    @ParameterizedTest(name = "{0} amount={1}")
    @MethodSource("invalidAmounts")
    void rejectsAmountBeyondCurrencyScale(CurrencyCode currencyCode, String amount) {
        // 통화별 허용 소수 자릿수를 초과한 금액에 대해서는 오류가 발생해야 한다.
        // 해당 테스트는 예외가 발생하면 테스트 통과, 예외가 발생하지 않으면 테스트가 실패한다.
        assertThatThrownBy(() -> currencyCode.validateAmountScale(new BigDecimal(amount)))
                .isInstanceOf(RuntimeException.class);
    }

    // Test의 파라미터로 전달할 Arguments 객체가 여러개 들어있는 Stream을 반환한다.
    // ParameterizedTest에서는 Stream에서 Arguments 객체를 하나씩 꺼내면서 파라미터로 전달하여 테스트를 수행한다.
    private static Stream<Arguments> currencyMinimums() {
        return Stream.of(
                Arguments.of(CurrencyCode.KRW, "1"),
                Arguments.of(CurrencyCode.USD, "0.01")
        );
    }
    // Test의 파라미터로 전달할 Arguments 객체가 여러개 들어있는 Stream을 반환한다.
    // ParameterizedTest에서는 Stream에서 Arguments 객체를 하나씩 꺼내면서 파라미터로 전달하여 테스트를 수행한다.
    private static Stream<Arguments> validAmounts() {
        return Stream.of(
                Arguments.of(CurrencyCode.KRW, "1000"),
                Arguments.of(CurrencyCode.KRW, "1000.0"),
                Arguments.of(CurrencyCode.USD, "10"),
                Arguments.of(CurrencyCode.USD, "10.01")
        );
    }
    // Test의 파라미터로 전달할 Arguments 객체가 여러개 들어있는 Stream을 반환한다.
    // ParameterizedTest에서는 Stream에서 Arguments 객체를 하나씩 꺼내면서 파라미터로 전달하여 테스트를 수행한다.
    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of(CurrencyCode.KRW, "0.1"),
                Arguments.of(CurrencyCode.USD, "0.001")
        );
    }
}
