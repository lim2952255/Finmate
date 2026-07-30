package com.finmate.domain.stock.market;

import java.time.LocalTime;
import java.time.ZoneId;

// 시장별 정규장, 애프터마켓, 일봉 반영 기준 시간을 담는다.
public record StockMarketSchedule(
        ZoneId zoneId,
        LocalTime regularOpenTime,
        LocalTime regularCloseTime,
        LocalTime afterHoursOpenTime,
        LocalTime afterHoursCloseTime,
        LocalTime dailyPriceAvailableTime
) {
    // 장 정규시간 + 애프터마켓 시간대를 검사한다.
    public boolean isRegularOrAfterHoursTradingTime(LocalTime time) {
        return isBetween(time, regularOpenTime, regularCloseTime)
                || isBetween(time, afterHoursOpenTime, afterHoursCloseTime);
    }

    private static boolean isBetween(LocalTime time, LocalTime startTime, LocalTime endTime) {
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }
}
