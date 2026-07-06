package com.finmate.domain.stock.dto.detail;

import lombok.Getter;

// 가격 축 레벨 그리기 위한 dto
@Getter
public class StockPriceAxisLabel {
    private final String y;
    private final String displayValue;

    public StockPriceAxisLabel(String y, String displayValue) {
        this.y = y;
        this.displayValue = displayValue;
    }
}
