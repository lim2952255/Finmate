package com.finmate.domain.normal.accountTransaction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public enum TransactionPeriod {
    TWO_WEEKS("최근 2주"),
    ONE_MONTH("최근 1개월"),
    THREE_MONTHS("최근 3개월");

    private final String displayName;

    public LocalDateTime getStartDateTime(LocalDateTime endDateTime) {
        return switch (this) {
            case TWO_WEEKS -> endDateTime.minusWeeks(2);
            case ONE_MONTH -> endDateTime.minusMonths(1);
            case THREE_MONTHS -> endDateTime.minusMonths(3);
        };
    }
}
