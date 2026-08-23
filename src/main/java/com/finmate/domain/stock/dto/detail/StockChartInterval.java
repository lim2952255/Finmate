package com.finmate.domain.stock.dto.detail;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 차트의 봉 주기를 관리하는 ENUM(일봉 / 주봉 / 월봉 / 연봉)
public enum StockChartInterval {
    MINUTE_1("1분봉", null, 1),
    MINUTE_3("3분봉", null, 3),
    MINUTE_5("5분봉", null, 5),
    MINUTE_15("15분봉", null, 15),
    DAY("일봉", "D"),
    WEEK("주봉", "W"),
    MONTH("월봉", "M"),
    YEAR("연봉", "Y");

    private final String displayName;
    private final String kisPeriodCode;
    private final int minuteSize;

    StockChartInterval(String displayName, String kisPeriodCode) {
        this(displayName, kisPeriodCode, 0);
    }

    StockChartInterval(String displayName, String kisPeriodCode, int minuteSize) {
        this.displayName = displayName;
        this.kisPeriodCode = kisPeriodCode;
        this.minuteSize = minuteSize;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKisPeriodCode() {
        if (isMinute()) {
            throw new IllegalStateException("분봉은 기간봉 KIS 코드를 사용하지 않습니다.");
        }
        return kisPeriodCode;
    }

    public boolean isMinute() {
        return minuteSize > 0;
    }

    public int getMinuteSize() {
        if (!isMinute()) {
            throw new IllegalStateException("분봉 주기가 아닙니다.");
        }
        return minuteSize;
    }

	// 파라미터로 전달받은 날짜가 속한 봉의 시작 날짜를 구한다
	// 2026년 8월 19일(수요일)이라고 할때
	// DAY: 8월 19일, WEEK: 8월 17일(월요일), MONTH: 8월 1일, YEAR: 2026년 1월 1일
    public LocalDate periodStart(LocalDate date) {
        return switch (this) {
            case MINUTE_1, MINUTE_3, MINUTE_5, MINUTE_15 -> date;
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); // 해당날짜를 기준으로 가장 가까운 이전 월요일 찾기
            case MONTH -> date.withDayOfMonth(1); // 해당 월의 1일로 변경
            case YEAR -> date.withDayOfYear(1); // 해당 연도의 첫번째 날로 변경
        };
    }

	// 현재 기간 이전에 완전히 끝난 기간의 마지막 날짜. 즉 마지막으로 확정된 봉의 날짜를 의미한다.
	// 2026년 8월 19일(수요일)이라고 할때
	// DAY: 8월 18일, WEEK: 8월 16일(일요일), MONTH: 7월 31일, YEAR: 2025년 12월 31일
    public LocalDate previousCompletedPeriodEnd(LocalDate date) {
        return periodStart(date).minusDays(1);
    }

	// 파라미터로 전달받은 날짜를 기준으로 다음 기준일을 결정한다.
    public LocalDate nextPeriodStart(LocalDate date) {
        LocalDate start = periodStart(date); // 현재 날짜가 속한 기간의 시작일
        return switch (this) {
            case MINUTE_1, MINUTE_3, MINUTE_5, MINUTE_15 -> start.plusDays(1);
            case DAY -> start.plusDays(1); // 기준일 + 1일
            case WEEK -> start.plusWeeks(1); // 기준일 + 일주일
            case MONTH -> start.plusMonths(1); // 기준일 + 1달
            case YEAR -> start.plusYears(1); // 기준일 + 1년
        };
    }
	// 파라미터로 전달받은 날찌를 기준으로 해당 날짜가 속한 기간의 종료일을 리턴한다.
    public LocalDate periodEnd(LocalDate date) {
        return nextPeriodStart(date).minusDays(1);
    }
}
