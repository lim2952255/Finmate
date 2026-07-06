package com.finmate.domain.stock.market;

import java.time.LocalTime;
import java.time.ZoneId;

// 시장별 시간 규칙을 담는 객체
public record StockMarketSchedule(
        ZoneId zoneId,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime dailyPriceAvailableTime
) {
}
