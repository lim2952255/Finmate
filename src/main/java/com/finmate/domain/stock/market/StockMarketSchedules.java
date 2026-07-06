package com.finmate.domain.stock.market;

import com.finmate.domain.stock.StockMarketType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

// 시장별 타임존, 장 운영 시간, 일봉 반영 기준 시간을 한 곳에서 관리한다.
public final class StockMarketSchedules {
    private static final ZoneId DOMESTIC_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId NASDAQ_ZONE = ZoneId.of("America/New_York");

    private static final StockMarketSchedule DOMESTIC_SCHEDULE = new StockMarketSchedule(
            DOMESTIC_ZONE,
            LocalTime.of(9, 0),
            LocalTime.of(15, 30),
            LocalTime.of(16, 0));
    private static final StockMarketSchedule NASDAQ_SCHEDULE = new StockMarketSchedule(
            NASDAQ_ZONE,
            LocalTime.of(9, 30),
            LocalTime.of(16, 0),
            LocalTime.of(16, 0));

    private StockMarketSchedules() {
    }

    public static StockMarketSchedule get(StockMarketType marketType) {
        return switch (marketType) {
            case KOSPI, KOSDAQ -> DOMESTIC_SCHEDULE;
            case NASDAQ -> NASDAQ_SCHEDULE;
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
