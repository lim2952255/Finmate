package com.finmate.domain.stock.dto.detail;

import lombok.Getter;

// 차트 하단 날짜 축 라벨을 그리기 위한 DTO
@Getter
public class StockDateAxisLabel {
    private final String x;
    private final String displayValue;
    private final String textAnchor;

    public StockDateAxisLabel(String x, String displayValue, String textAnchor) {
        this.x = x;
        this.displayValue = displayValue;
        this.textAnchor = textAnchor;
    }
}
