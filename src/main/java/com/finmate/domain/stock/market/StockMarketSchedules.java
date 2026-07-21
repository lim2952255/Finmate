package com.finmate.domain.stock.market;

import com.finmate.domain.stock.StockMarketType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// 시장별 타임존, 장 운영 시간, 일봉 반영 기준 시간을 한 곳에서 관리한다.
public final class StockMarketSchedules {
    private static final ZoneId DOMESTIC_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId NASDAQ_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final StockMarketSchedule DOMESTIC_SCHEDULE = new StockMarketSchedule(
            DOMESTIC_ZONE,
            LocalTime.of(9, 0),
            LocalTime.of(15, 30),
            LocalTime.of(15, 40),
            LocalTime.of(18, 0),
            LocalTime.of(16, 0));
    private static final StockMarketSchedule NASDAQ_SCHEDULE = new StockMarketSchedule(
            NASDAQ_ZONE,
            LocalTime.of(9, 30),
            LocalTime.of(16, 0),
            LocalTime.of(16, 0),
            LocalTime.of(20, 0),
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

    public static boolean isTradingTime(StockMarketType marketType, ZonedDateTime dateTime) {
        StockMarketSchedule schedule = get(marketType);
        ZonedDateTime marketDateTime = dateTime.withZoneSameInstant(schedule.zoneId());
        return isWeekday(marketDateTime.getDayOfWeek())
                && schedule.isTradingTime(marketDateTime.toLocalTime());
    }

    public static boolean isTradingTimeNow(StockMarketType marketType) {
        return isTradingTime(marketType, ZonedDateTime.now());
    }

    public static String tradingTimeDescription(StockMarketType marketType) {
        return tradingTimeDescription(marketType, ZonedDateTime.now());
    }

    public static LocalDate expectedLatestDailyPriceTradeDate(StockMarketType marketType) {
        StockMarketSchedule schedule = get(marketType);
        LocalDate today = LocalDate.now(schedule.zoneId());
        LocalTime now = LocalTime.now(schedule.zoneId());

        LocalDate candidate = now.isBefore(schedule.dailyPriceAvailableTime()) ? today.minusDays(1) : today;
        return previousWeekday(candidate);
    }

    public static String tradingTimeDescription(StockMarketType marketType, ZonedDateTime referenceDateTime) {
        StockMarketSchedule schedule = get(marketType);
        if (schedule.zoneId().equals(KOREA_ZONE)) {
            return "대한민국 시간 기준 %s".formatted(schedule.tradingTimeDescription());
        }

        LocalDate marketDate = nextWeekday(referenceDateTime.withZoneSameInstant(schedule.zoneId()).toLocalDate());
        return "현지 시각(%s) 기준 %s / 대한민국 시간 기준 %s".formatted(
                schedule.zoneId(),
                schedule.tradingTimeDescription(),
                koreaTradingTimeDescription(schedule, marketDate));
    }

    private static LocalDate nextWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }

        return candidate;
    }

    private static String koreaTradingTimeDescription(StockMarketSchedule schedule, LocalDate marketDate) {
        return "%s, %s".formatted(
                koreaSessionDescription(schedule, marketDate, schedule.openTime(), schedule.closeTime()),
                koreaSessionDescription(schedule, marketDate, schedule.afterHoursOpenTime(), schedule.afterHoursCloseTime()));
    }

    // 현재 거래시간을 한국 시간 기준으로 표시
    private static String koreaSessionDescription(StockMarketSchedule schedule,
                                                  LocalDate marketDate,
                                                  LocalTime startTime,
                                                  LocalTime endTime) {
        ZonedDateTime startDateTime = ZonedDateTime.of(marketDate, startTime, schedule.zoneId())
                .withZoneSameInstant(KOREA_ZONE);
        ZonedDateTime endDateTime = ZonedDateTime.of(marketDate, endTime, schedule.zoneId())
                .withZoneSameInstant(KOREA_ZONE);
        return "%s~%s".formatted(
                koreaTimeText(startDateTime, marketDate),
                koreaTimeText(endDateTime, marketDate));
    }

    private static String koreaTimeText(ZonedDateTime koreaDateTime, LocalDate marketDate) {
        String timeText = koreaDateTime.toLocalTime().format(TIME_FORMATTER);
        long dayOffset = ChronoUnit.DAYS.between(marketDate, koreaDateTime.toLocalDate());
        if (dayOffset == 0) {
            return timeText;
        }
        if (dayOffset > 0) {
            return "%s(+%d일)".formatted(timeText, dayOffset);
        }
        return "%s(%d일)".formatted(timeText, dayOffset);
    }
}
