package com.finmate.domain.stock.market;

import com.finmate.domain.stock.StockMarketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 국내 시장과 해외시장(미국시장)의 장 오픈시간대를 검증한다.
class StockMarketTradingHoursTest {

    @DisplayName("ORD-001: 국내 시장은 정규장과 시간외 거래 경계를 포함한다")
    @ParameterizedTest(name = "{0} open={1}")
    @MethodSource("domesticTradingBoundaries")
    void evaluatesDomesticTradingBoundaries(String localDateTime, boolean expectedOpen) {
        ZonedDateTime reference = ZonedDateTime.parse(localDateTime + "+09:00[Asia/Seoul]");

        // 지정한 localDateTime에 장이 Open또는 Close되어있는지를 검증한다.
        assertThat(StockMarketSchedules.isMarketTradingTime(StockMarketType.KOSPI, reference))
                .isEqualTo(expectedOpen);
        assertThat(StockMarketSchedules.isMarketTradingTime(StockMarketType.KOSDAQ, reference))
                .isEqualTo(expectedOpen);
    }

    @DisplayName("ORD-001: NASDAQ은 뉴욕 현지 프리·정규·애프터마켓 경계를 포함한다")
    @ParameterizedTest(name = "{0} open={1}")
    @MethodSource("nasdaqTradingBoundaries")
    void evaluatesNasdaqTradingBoundaries(String localDateTime, boolean expectedOpen) {
        ZonedDateTime reference = ZonedDateTime.of(
                java.time.LocalDateTime.parse(localDateTime), ZoneId.of("America/New_York"));

        // 지정한 localDateTime에 장이 Open또는 Close되어있는지를 검증한다.
        assertThat(StockMarketSchedules.isMarketTradingTime(StockMarketType.NASDAQ, reference))
                .isEqualTo(expectedOpen);
    }

    @DisplayName("ORD-001: NASDAQ 정규장 시작은 미국 DST에 따라 서로 다른 한국 시각에 열린다")
    @ParameterizedTest(name = "KST {0}")
    @MethodSource("nasdaqDstOpenTimesInKorea")
    void observesNasdaqDaylightSavingShiftFromKorea(String koreaDateTime) {
        // 국내 시간 기준
        ZonedDateTime reference = ZonedDateTime.parse(koreaDateTime + "+09:00[Asia/Seoul]");

        assertThat(StockMarketSchedules.isMarketTradingTime(StockMarketType.NASDAQ, reference)).isTrue();
        assertThat(reference.withZoneSameInstant(ZoneId.of("America/New_York")).toLocalTime())
                .isEqualTo(java.time.LocalTime.of(9, 30));
    }

    @DisplayName("ORD-001: 모든 시장은 주말에 닫힌다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("weekendMarketTimes")
    void closesMarketsOnWeekend(StockMarketType marketType, ZonedDateTime weekendTime) {
        assertThat(StockMarketSchedules.isMarketTradingTime(marketType, weekendTime)).isFalse();
    }

    // Test에 전달할 파라미터(Arguments)들을 Stream에 담아서 전달한다.
    private static Stream<Arguments> domesticTradingBoundaries() {
        return Stream.of(
                // 국내 장 오픈시간: 0900 ~ 1530, 1540 ~ 1800
                Arguments.of("2026-07-23T08:59:59", false),
                Arguments.of("2026-07-23T09:00:00", true),
                Arguments.of("2026-07-23T15:30:00", true),
                Arguments.of("2026-07-23T15:30:01", false),
                Arguments.of("2026-07-23T15:40:00", true),
                Arguments.of("2026-07-23T18:00:00", true),
                Arguments.of("2026-07-23T18:00:01", false)
        );
    }

    // Test에 전달할 파라미터(Arguments)들을 Stream에 담아서 전달한다.
    private static Stream<Arguments> nasdaqTradingBoundaries() {
        return Stream.of(
                Arguments.of("2026-07-23T03:59:59", false),
                Arguments.of("2026-07-23T04:00:00", true),
                Arguments.of("2026-07-23T09:29:59", true),
                Arguments.of("2026-07-23T09:30:00", true),
                Arguments.of("2026-07-23T16:00:00", true),
                Arguments.of("2026-07-23T20:00:00", true),
                Arguments.of("2026-07-23T20:00:01", false)
        );
    }

    // Test에 전달할 파라미터(Arguments)들을 Stream에 담아서 전달한다.
    private static Stream<Arguments> nasdaqDstOpenTimesInKorea() {
        return Stream.of(
                Arguments.of("2026-01-06T23:30:00"),
                Arguments.of("2026-07-06T22:30:00")
        );
    }

    // Test에 전달할 파라미터(Arguments)들을 Stream에 담아서 전달한다.
    private static Stream<Arguments> weekendMarketTimes() {
        return Stream.of(
                Arguments.of(StockMarketType.KOSPI,
                        ZonedDateTime.parse("2026-07-25T10:00:00+09:00[Asia/Seoul]")),
                Arguments.of(StockMarketType.KOSDAQ,
                        ZonedDateTime.parse("2026-07-26T10:00:00+09:00[Asia/Seoul]")),
                Arguments.of(StockMarketType.NASDAQ,
                        ZonedDateTime.of(2026, 7, 25, 10, 0, 0, 0, ZoneId.of("America/New_York")))
        );
    }
}
