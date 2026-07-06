package com.finmate.service.stock.ranking;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.market.StockMarketSchedule;
import com.finmate.domain.stock.market.StockMarketSchedules;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

// 특정 주식시장의 정규장이 열려있는지 + 장 마감 직후 마지막 랭킹을 갱신해야 하는 시간인지를 판단하는 서비스로직
@Service
public class StockMarketSessionService {
    // 장 마감 직후 추가 갱신 허용 시간(기본값: 2분)
    private static final Duration FINAL_REFRESH_WINDOW = Duration.ofMinutes(2);

    // 해당 시장의 랭킹을 지금 갱신해야 하는지 여부 판단 (장이 열려 있거나, 장 마감 직후 2분 이내면 갱신한다)
    public boolean shouldRefreshRanking(StockMarketType marketType) {
        return isMarketOpen(marketType) || isFinalRefreshWindow(marketType);
    }

    // 해당 시장의 정규장이 열려있는지 여부를 반환한다.
    public boolean isMarketOpen(StockMarketType marketType) {
        // 해당 시장의 운영정보 (장 시작 시간 / 종료시간)을 받는다.
        StockMarketSchedule schedule = StockMarketSchedules.get(marketType);
        // 해당 시장의 시간대를 기준으로 현재 시간을 구한다.
        ZonedDateTime now = ZonedDateTime.now(schedule.zoneId());

        // 현재 날짜가 평일인지를 확인 (정규장은 평일에만 열리기 떄문에, 주말인 경우에는 false를 리턴한다)
        if (!StockMarketSchedules.isWeekday(now.getDayOfWeek())) {
            return false;
        }

        // 현재 시간이 해당 시장의 정규장 시작 시간과 종료 시간 사이인지를 확인하고 결과를 리턴한다.
        LocalTime currentTime = now.toLocalTime();
        return !currentTime.isBefore(schedule.openTime()) && currentTime.isBefore(schedule.closeTime());
    }

    // 해당 시장의 장 마감 직후 2분 이내인지 확인하는 메서드
    private boolean isFinalRefreshWindow(StockMarketType marketType) {
        // 해당 시장의 운영정보 (장 시작 시간 / 종료시간)을 받는다.
        StockMarketSchedule schedule = StockMarketSchedules.get(marketType);
        // 해당 시장의 시간대를 기준으로 현재 시간을 구한다.
        ZonedDateTime now = ZonedDateTime.now(schedule.zoneId());

        // 현재 날짜가 평일인지를 확인 (정규장은 평일에만 열리기 떄문에, 주말인 경우에는 false를 리턴한다)
        if (!StockMarketSchedules.isWeekday(now.getDayOfWeek())) {
            return false;
        }

        // 현재 시간이 해당 시장의 장 마감시간 ~ 장 마감직후 2분 이내에 포함되는지를 확인하고 결과를 리턴한다.
        LocalTime currentTime = now.toLocalTime();
        LocalTime finalRefreshEndTime = schedule.closeTime().plus(FINAL_REFRESH_WINDOW);
        return !currentTime.isBefore(schedule.closeTime()) && currentTime.isBefore(finalRefreshEndTime);
    }
}
