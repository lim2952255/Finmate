package com.finmate.domain.stock.market;

import java.time.LocalTime;
import java.time.ZoneId;

// 시장별 시간 규칙을 담는 객체 (장 오픈 시간 / 장 마감시간 + 애프터마켓 시간)
public record StockMarketSchedule(
        ZoneId zoneId,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime afterHoursOpenTime,
        LocalTime afterHoursCloseTime,
        LocalTime dailyPriceAvailableTime
) {
    public boolean isTradingTime(LocalTime time) {
        return isBetween(time, openTime, closeTime)
                || isBetween(time, afterHoursOpenTime, afterHoursCloseTime);
    }

    public String tradingTimeDescription() {
        return "%s~%s, %s~%s".formatted(
                openTime,
                closeTime,
                afterHoursOpenTime,
                afterHoursCloseTime);
    }

    private boolean isBetween(LocalTime time, LocalTime startTime, LocalTime endTime) {
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }
}
