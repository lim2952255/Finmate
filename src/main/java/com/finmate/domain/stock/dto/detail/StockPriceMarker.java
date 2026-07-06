package com.finmate.domain.stock.dto.detail;

import lombok.Getter;

// 차트 내 최고가/최저가 지점을 표시하기 위한 DTO
@Getter
public class StockPriceMarker {
    private final String pointX;
    private final String pointY;
    private final String labelX;
    private final String labelY;
    private final String textAnchor;
    private final String label;
    private final String cssClass;
    private final String price;
    private final String tradeDate;

    public StockPriceMarker(String pointX,
                            String pointY,
                            String labelX,
                            String labelY,
                            String textAnchor,
                            String label,
                            String cssClass,
                            String price,
                            String tradeDate) {
        this.pointX = pointX;
        this.pointY = pointY;
        this.labelX = labelX;
        this.labelY = labelY;
        this.textAnchor = textAnchor;
        this.label = label;
        this.cssClass = cssClass;
        this.price = price;
        this.tradeDate = tradeDate;
    }
}
