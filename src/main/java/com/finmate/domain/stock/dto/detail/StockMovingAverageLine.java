package com.finmate.domain.stock.dto.detail;

import lombok.Getter;

// MA5, MA20, MA60 이동평균선을 그리기 위한 dto
@Getter
public class StockMovingAverageLine {
    private final String label;
    private final String cssClass;
    private final String color;
    private final String points;

    public StockMovingAverageLine(String label, String cssClass, String color, String points) {
        this.label = label;
        this.cssClass = cssClass;
        this.color = color;
        this.points = points;
    }
}
