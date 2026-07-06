package com.finmate.domain.stock.dto.detail;

import java.time.LocalDate;

public enum StockChartPeriod {
    ONE_WEEK("1주", 7),
    ONE_MONTH("1개월", 1),
    THREE_MONTHS("3개월", 3),
    SIX_MONTHS("6개월", 6),
    ONE_YEAR("1년", 12),
    THREE_YEARS("3년", 36);

    private final String displayName;
    private final int amount;

    StockChartPeriod(String displayName, int amount) {
        this.displayName = displayName;
        this.amount = amount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getStartDate(LocalDate endDate) {
        if (this == ONE_WEEK) {
            return endDate.minusDays(amount);
        }

        return endDate.minusMonths(amount);
    }
}
