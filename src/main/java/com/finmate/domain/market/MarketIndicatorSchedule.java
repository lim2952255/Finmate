package com.finmate.domain.market;

import java.time.LocalTime;
import java.time.ZoneId;

// 환율/지수별 시간 규칙을 담는 객체
public record MarketIndicatorSchedule(
        ZoneId zoneId,
        LocalTime dailyPriceAvailableTime
) {
}
