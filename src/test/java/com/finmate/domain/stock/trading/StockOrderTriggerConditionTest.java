package com.finmate.domain.stock.trading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 예약 주문의 예약 조건 충족조건을 검증한다.
class StockOrderTriggerConditionTest {

    @DisplayName("ORD-002: 이상 조건은 현재가가 기준가 이상일 때만 충족한다")
    @ParameterizedTest(name = "current={0}, trigger={1}, satisfied={2}")
    @CsvSource({"101,100,true", "100,100,true", "99,100,false"}) // 테스트에 전달할 Arguments를 직접 명시
    void priceAtOrAboveUsesInclusiveBoundary(String current, String trigger, boolean expected) {
        // 이상 조건의 경우, 현재가가 기준가 이상일때만 예약이 trigger되어야 한다.
        boolean satisfied = StockOrderTriggerCondition.PRICE_AT_OR_ABOVE
                .isSatisfied(new BigDecimal(current), new BigDecimal(trigger));

        assertThat(satisfied).isEqualTo(expected);
    }

    @DisplayName("ORD-002: 이하 조건은 현재가가 기준가 이하일 때만 충족한다")
    @ParameterizedTest(name = "current={0}, trigger={1}, satisfied={2}")
    @CsvSource({"99,100,true", "100,100,true", "101,100,false"})
    void priceAtOrBelowUsesInclusiveBoundary(String current, String trigger, boolean expected) {
        // 이하 조건의 경우, 현재가가 기준가 이하일때만 예약이 trigger되어야 한다.
        boolean satisfied = StockOrderTriggerCondition.PRICE_AT_OR_BELOW
                .isSatisfied(new BigDecimal(current), new BigDecimal(trigger));

        assertThat(satisfied).isEqualTo(expected);
    }
}
