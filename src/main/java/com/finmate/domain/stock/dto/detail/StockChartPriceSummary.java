package com.finmate.domain.stock.dto.detail;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 선택 기간의 최고가와 최저가를 화면 요약 영역에 전달한다.
 * 픽셀 좌표나 SVG 표현 정보는 포함하지 않는다.
 */
public record StockChartPriceSummary(
        BigDecimal highestPrice,
        LocalDate highestTradeDate,
        BigDecimal lowestPrice,
        LocalDate lowestTradeDate
) {
}
