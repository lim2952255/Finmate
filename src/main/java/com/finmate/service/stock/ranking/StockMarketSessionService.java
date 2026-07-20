package com.finmate.service.stock.ranking;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedule;
import com.finmate.domain.stock.market.StockMarketSchedules;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

// 특정 주식시장의 거래 가능 시간인지 + 장 마감 직후 마지막 랭킹을 갱신해야 하는 시간인지를 판단하는 서비스로직
@Service
public class StockMarketSessionService {
    // 장 마감 직후 추가 갱신 허용 시간(기본값: 2분)
    private static final Duration FINAL_REFRESH_WINDOW = Duration.ofMinutes(2);

    // 해당 시장의 랭킹을 지금 갱신해야 하는지 여부 판단 (정규장/시간외 거래 시간이거나, 장 마감 직후 2분 이내면 갱신한다)
    public boolean shouldRefreshRanking(StockMarketType marketType) {
        return shouldRefreshRanking(marketType, ZonedDateTime.now());
    }

    // 해당 시장의 정규장 또는 시간외 거래 시간이 열려있는지 여부를 반환한다.
    public boolean isMarketOpen(StockMarketType marketType) {
        return isMarketOpen(marketType, ZonedDateTime.now());
    }

    boolean shouldRefreshRanking(StockMarketType marketType, ZonedDateTime referenceDateTime) {
        return isMarketOpen(marketType, referenceDateTime) || isFinalRefreshWindow(marketType, referenceDateTime);
    }

    boolean isMarketOpen(StockMarketType marketType, ZonedDateTime referenceDateTime) {
        return StockMarketSchedules.isTradingTime(marketType, referenceDateTime);
    }

    // 해당 시장의 정규장 또는 시간외 장 마감 직후 2분 이내인지 확인하는 메서드
    private boolean isFinalRefreshWindow(StockMarketType marketType, ZonedDateTime referenceDateTime) {
        // 해당 시장의 운영정보 (장 시작 시간 / 종료시간)을 받는다.
        StockMarketSchedule schedule = StockMarketSchedules.get(marketType);
        // 해당 시장의 시간대를 기준으로 현재 시간을 구한다.
        ZonedDateTime marketDateTime = referenceDateTime.withZoneSameInstant(schedule.zoneId());

        // 현재 날짜가 평일인지를 확인 (거래 가능 시간은 평일에만 열리기 때문에, 주말인 경우에는 false를 리턴한다)
        if (!StockMarketSchedules.isWeekday(marketDateTime.getDayOfWeek())) {
            return false;
        }

        return isWithinFinalRefreshWindow(marketDateTime, schedule.closeTime())
                || isWithinFinalRefreshWindow(marketDateTime, schedule.afterHoursCloseTime());
    }

    private boolean isWithinFinalRefreshWindow(ZonedDateTime marketDateTime, LocalTime closeTime) {
        LocalTime currentTime = marketDateTime.toLocalTime();
        LocalTime finalRefreshEndTime = closeTime.plus(FINAL_REFRESH_WINDOW);
        return !currentTime.isBefore(closeTime) && currentTime.isBefore(finalRefreshEndTime);
    }
}
