package com.finmate.domain.stock.dto.trading;

import java.math.BigDecimal;
import java.time.LocalDate;

// 포트폴리오에서 수익률을 계산하기 위한 주가 주가의 출처 정보(실시간 시세 or 최근 종가)를 담은 레코드
public record StockPortfolioPriceSnapshot(
        BigDecimal price,
        LocalDate tradeDate,
        String sourceName
) {
    public String sourceText() {
        if (tradeDate == null || sourceName == null || sourceName.isBlank()) {
            return "";
        }

        return sourceName + " " + tradeDate;
    }
}
