package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.stock.StockMarketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 종목 매수/매도시에 거래 수수료와 세금이 제대로 적용되는지를 검사한다.

class StockTradingFeePolicyTest {

    @DisplayName("ORD-014: 시장별 수수료를 통화 단위 HALF_UP으로 계산한다")
    @ParameterizedTest(name = "{0} gross={2} commission={3}")
    @MethodSource("commissionCases")
    void calculatesCommissionHalfUp(StockMarketType marketType,
                                    CurrencyCode currencyCode,
                                    String grossAmount, // 수수료 + 세금을 적용하기 전 거래대금
                                    String expectedCommission) { // 예상 수수료
        BigDecimal commission = StockTradingFeePolicy.from(marketType) // 시장 종류에 맞는 수수료 정책(수수로율)을 가져온다.
                .calculateCommissionAmount(currencyCode, new BigDecimal(grossAmount));  // 통화별로 실제 수수료를 계산한 후, 소수점 단위를 반올림한다.

        // 예상 수수료와 실제 수수료를 검증한다.
        assertThat(commission).isEqualByComparingTo(expectedCommission);
    }

    @DisplayName("ORD-014: 매수 세금은 통화 scale의 0이다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("marketCurrencies")
    void buyTaxIsZeroAtCurrencyScale(StockMarketType marketType, CurrencyCode currencyCode) {
        BigDecimal tax = StockTradingFeePolicy.from(marketType)
                .calculateTaxAmount(currencyCode, StockOrderSide.BUY, new BigDecimal("10000"));

        // 매수세는 존재하지 않는다.
        assertThat(tax).isEqualByComparingTo("0");
        // currencyCode.getFractionDigits()은 해당 통화 소수점 아래 몇자리까지 사용하는지를 반환하는 메서드이다.
        // 따라서 세금값의 소수점 자릿수가 통화의 소수점 규칙을 따르는지를 검증한다.
        assertThat(tax.scale()).isEqualTo(currencyCode.getFractionDigits());
    }

    @DisplayName("ORD-014: 시장별 매도세를 통화 단위 HALF_UP으로 계산한다")
    @ParameterizedTest(name = "{0} gross={2} tax={3}")
    @MethodSource("sellTaxCases")
    void calculatesSellTaxHalfUp(StockMarketType marketType,
                                 CurrencyCode currencyCode,
                                 String grossAmount,
                                 String expectedTax) {
        BigDecimal tax = StockTradingFeePolicy.from(marketType)
                .calculateTaxAmount(currencyCode, StockOrderSide.SELL, new BigDecimal(grossAmount));

        // 통화별 매도세를 계산 후 예상 매도세와 검증한다.
        assertThat(tax).isEqualByComparingTo(expectedTax);
    }

    @Test
    @DisplayName("ORD-014: 매수 정산액은 거래대금과 수수료의 합이다")
    void buySettlementAddsCommission() {
        BigDecimal settlement = StockTradingFeePolicy.KOSPI.calculateSettlementAmount(
                CurrencyCode.KRW, StockOrderSide.BUY, new BigDecimal("10000")); // 매수 정산액 계산

        // 매수 정산액: 거래대금 + 거래 수수료
        assertThat(settlement).isEqualByComparingTo("10002");
    }

    @Test
    @DisplayName("ORD-014: 매도 정산액은 거래대금에서 수수료와 세금을 뺀 금액이다")
    void sellSettlementSubtractsCommissionAndTax() {
        BigDecimal settlement = StockTradingFeePolicy.KOSPI.calculateSettlementAmount(
                CurrencyCode.KRW, StockOrderSide.SELL, new BigDecimal("10000")); // 매도 정산액 계산

        // 매도 정산액: 거래대금 - 거래 수수료 - 매도세
        assertThat(settlement).isEqualByComparingTo("9978");
    }

    // Test에 파라미터로 넘겨줄 인자(Arguments)들을 Stream에 담아서 반환한다.
    private static Stream<Arguments> commissionCases() {
        return Stream.of(
                Arguments.of(StockMarketType.KOSPI, CurrencyCode.KRW, "10000", "2"),
                Arguments.of(StockMarketType.KOSDAQ, CurrencyCode.KRW, "9999", "1"),
                Arguments.of(StockMarketType.NASDAQ, CurrencyCode.USD, "10.00", "0.03")
        );
    }

    // Test에 파라미터로 넘겨줄 인자(Arguments)들을 Stream에 담아서 반환한다.
    private static Stream<Arguments> sellTaxCases() { // 매도세
        return Stream.of(
                Arguments.of(StockMarketType.KOSPI, CurrencyCode.KRW, "10000", "20"),
                Arguments.of(StockMarketType.KOSDAQ, CurrencyCode.KRW, "10250", "21"),
                Arguments.of(StockMarketType.NASDAQ, CurrencyCode.USD, "10000.00", "0.21")
        );
    }

    // Test에 파라미터로 넘겨줄 인자(Arguments)들을 Stream에 담아서 반환한다.
    private static Stream<Arguments> marketCurrencies() {
        return Stream.of(
                Arguments.of(StockMarketType.KOSPI, CurrencyCode.KRW),
                Arguments.of(StockMarketType.KOSDAQ, CurrencyCode.KRW),
                Arguments.of(StockMarketType.NASDAQ, CurrencyCode.USD)
        );
    }
}
