package com.finmate.domain.stock.market;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collectors;

// 시장별 타임존, 장 운영 시간, 일봉 반영 기준 시간을 한 곳에서 관리한다.
public final class StockMarketSchedules {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId NASDAQ_ZONE = ZoneId.of("America/New_York");
    // NASDAQ 프리마켓 시간
    private static final LocalTime NASDAQ_PRE_MARKET_OPEN_TIME = LocalTime.of(4, 0);
    private static final LocalTime NASDAQ_PRE_MARKET_CLOSE_TIME = LocalTime.of(9, 30);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final StockMarketSchedule DOMESTIC_SCHEDULE = new StockMarketSchedule(
            KOREA_ZONE,
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

    public static StockMarketSchedule getSchedule(StockMarketType marketType) {
        return switch (marketType) {
            case KOSPI, KOSDAQ -> DOMESTIC_SCHEDULE;
            case NASDAQ -> NASDAQ_SCHEDULE;
        };
    }

    // 평일인지를 검사한다.
    public static boolean isWeekday(DayOfWeek dayOfWeek) {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    // 금일이 주말인경우, 지난주 금요일을 찾는다.
    private static LocalDate previousOrSameWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.minusDays(1);
        }

        return candidate;
    }

    // 시장 기준으로 거래 가능한 모든 공통 세션을 검사한다.
    // NASDAQ은 프리·정규·애프터마켓, 국내 시장은 KRX 정규·시간외시장에 해당한다.
    public static boolean isMarketTradingTime(StockMarketType marketType, ZonedDateTime dateTime) {
        StockMarketSchedule schedule = getSchedule(marketType); // 시장별 스케줄러를 조회
        ZonedDateTime marketDateTime = dateTime.withZoneSameInstant(schedule.zoneId());
        // 평일이 아닌경우는 false
        if (!isWeekday(marketDateTime.getDayOfWeek())) {
            return false;
        }

        LocalTime time = marketDateTime.toLocalTime();
        // 프리마켓 + 정규시간대 + 애프터마켓 시간대를 한번에 검사한다.
        return schedule.isRegularOrAfterHoursTradingTime(time)
                || isNasdaqPreMarketTime(marketType, time);
    }

    // 현재 거래가능한시간대인지를 검사한다.
    public static boolean isMarketTradingTimeNow(StockMarketType marketType) {
        return isMarketTradingTime(marketType, ZonedDateTime.now());
    }

    // 종목별 거래 가능 시간을 검사한다. 국내 종목은 NXT 세션 허용 코드까지 반영한다.
    public static boolean isTradingTime(Stock stock, ZonedDateTime dateTime) {
        if (stock == null) {
            return false;
        }

        // 종목이 거래기능한지 여부를 NXT 세션까지 검사해서 리턴한다.
        return isMarketTradingTime(stock.getMarketType(), dateTime)
                || isNxtTradingTime(stock, dateTime);
    }

    // 해당 종목이 현재 거래가 가능한지를 검사한다.
    public static boolean isTradingTimeNow(Stock stock) {
        return isTradingTime(stock, ZonedDateTime.now());
    }

    public static String describeTradingHours(StockMarketType marketType) {
        return describeTradingHours(marketType, ZonedDateTime.now());
    }

    // 일봉은 프리·애프터마켓과 무관하게 시장별 정규장 종가 반영 시각을 기준으로 계산한다.
    public static LocalDate expectedLatestDailyPriceTradeDate(StockMarketType marketType) {
        StockMarketSchedule schedule = getSchedule(marketType);
        LocalDate today = LocalDate.now(schedule.zoneId());
        LocalTime now = LocalTime.now(schedule.zoneId());

        // 일본반영시간대는 정규장 종가 기준
        LocalDate candidate = now.isBefore(schedule.dailyPriceAvailableTime()) ? today.minusDays(1) : today;
        return previousOrSameWeekday(candidate);
    }

    public static String describeTradingHours(StockMarketType marketType, ZonedDateTime referenceDateTime) {
        StockMarketSchedule schedule = getSchedule(marketType);
        if (schedule.zoneId().equals(KOREA_ZONE)) {
            return "KRX 대한민국 시간 기준 %s(정규), %s(시간외)".formatted(
                    timeRange(schedule.regularOpenTime(), schedule.regularCloseTime()),
                    timeRange(schedule.afterHoursOpenTime(), schedule.afterHoursCloseTime()));
        }

        LocalDate marketDate = nextOrSameWeekday(
                referenceDateTime.withZoneSameInstant(schedule.zoneId()).toLocalDate());
        String localSessionDescription = "%s(프리), %s(정규), %s(애프터)".formatted(
                timeRange(NASDAQ_PRE_MARKET_OPEN_TIME, NASDAQ_PRE_MARKET_CLOSE_TIME),
                timeRange(schedule.regularOpenTime(), schedule.regularCloseTime()),
                timeRange(schedule.afterHoursOpenTime(), schedule.afterHoursCloseTime()));
        return "현지 시각(%s) 기준 %s / 대한민국 시간 기준 %s".formatted(
                schedule.zoneId(),
                localSessionDescription,
                koreaNasdaqTradingTimeDescription(marketDate));
    }

    public static String describeTradingHours(Stock stock) {
        if (stock == null || stock.getMarketType() == StockMarketType.NASDAQ) {
            return describeTradingHours(stock == null ? StockMarketType.NASDAQ : stock.getMarketType());
        }

        String krxDescription = describeTradingHours(stock.getMarketType());
        if (stock.getNxtTradingPermissionCode() == null) {
            return krxDescription + " / NXT 비대상";
        }
        if (stock.getNxtTradingPermissionCode() == 0) {
            return krxDescription + " / NXT 대상(현재 거래 제한)";
        }
        String nxtSessions = Arrays.stream(NxtTradingSession.values())
                .filter(session -> session.isAllowed(stock.getNxtTradingPermissionCode()))
                .map(session -> "%s(%s)".formatted(
                        timeRange(session.getOpenTime(), session.getCloseTime()),
                        session.getDisplayName().replace("NXT ", "")))
                .collect(Collectors.joining(", "));
        return krxDescription + " / NXT 대한민국 시간 기준 " + nxtSessions;
    }

    // NASDAQ 프리마켓 오픈 시간대인지를 검사한다.
    private static boolean isNasdaqPreMarketTime(StockMarketType marketType, LocalTime time) {
        return marketType == StockMarketType.NASDAQ
                && !time.isBefore(NASDAQ_PRE_MARKET_OPEN_TIME)
                && time.isBefore(NASDAQ_PRE_MARKET_CLOSE_TIME);
    }

    // 종목이 NXT 거래가능 시간대인지를 검사한다.
    private static boolean isNxtTradingTime(Stock stock, ZonedDateTime dateTime) {
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return false;
        }

        ZonedDateTime koreaDateTime = dateTime.withZoneSameInstant(KOREA_ZONE);
        if (!isWeekday(koreaDateTime.getDayOfWeek())) {
            return false;
        }

        Integer permissionCode = stock.getNxtTradingPermissionCode();
        LocalTime time = koreaDateTime.toLocalTime();
        for (NxtTradingSession session : NxtTradingSession.values()) {
            if (session.isTradingTime(permissionCode, time)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate nextOrSameWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }

        return candidate;
    }

    private static String timeRange(LocalTime openTime, LocalTime closeTime) {
        return "%s~%s".formatted(openTime, closeTime);
    }

    private static String koreaNasdaqTradingTimeDescription(LocalDate marketDate) {
        return "%s(프리), %s(정규), %s(애프터)".formatted(
                koreaSessionDescription(
                        NASDAQ_SCHEDULE,
                        marketDate,
                        NASDAQ_PRE_MARKET_OPEN_TIME,
                        NASDAQ_PRE_MARKET_CLOSE_TIME),
                koreaSessionDescription(
                        NASDAQ_SCHEDULE,
                        marketDate,
                        NASDAQ_SCHEDULE.regularOpenTime(),
                        NASDAQ_SCHEDULE.regularCloseTime()),
                koreaSessionDescription(
                        NASDAQ_SCHEDULE,
                        marketDate,
                        NASDAQ_SCHEDULE.afterHoursOpenTime(),
                        NASDAQ_SCHEDULE.afterHoursCloseTime()));
    }

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
