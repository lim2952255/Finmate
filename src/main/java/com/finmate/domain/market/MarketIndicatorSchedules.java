package com.finmate.domain.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

// 환율/지수별 일봉 반영 기준 시간을 한 곳에서 관리한다.
public final class MarketIndicatorSchedules {
    private static final ZoneId EXCHANGE_RATE_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId DOMESTIC_INDEX_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId OVERSEAS_INDEX_ZONE = ZoneId.of("America/New_York");

    private static final MarketIndicatorSchedule EXCHANGE_RATE_SCHEDULE = new MarketIndicatorSchedule(
            EXCHANGE_RATE_ZONE,
            LocalTime.of(9, 0));
    private static final MarketIndicatorSchedule DOMESTIC_INDEX_SCHEDULE = new MarketIndicatorSchedule(
            DOMESTIC_INDEX_ZONE,
            LocalTime.of(16, 0));
    private static final MarketIndicatorSchedule OVERSEAS_INDEX_SCHEDULE = new MarketIndicatorSchedule(
            OVERSEAS_INDEX_ZONE,
            LocalTime.of(16, 0));

    private MarketIndicatorSchedules() {
    }

    public static MarketIndicatorSchedule get(MarketIndicatorSymbol indicator) {
        return switch (indicator) {
            // Indicator에 따른 Scheduler 정보를 리턴한다.
            case USD_KRW -> EXCHANGE_RATE_SCHEDULE;
            case KOSPI, KOSDAQ -> DOMESTIC_INDEX_SCHEDULE;
            case NASDAQ_COMPOSITE, NASDAQ_100 -> OVERSEAS_INDEX_SCHEDULE;
        };
    }

    public static boolean isWeekday(DayOfWeek dayOfWeek) {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    public static LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.minusDays(1);
        }

        return candidate;
    }
}
