package com.finmate.domain.stock.dto.detail;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// KIS API로부터 받은 분봉데이터를 저장하는 레코드
public record StockMinuteCandleData(
        LocalDateTime startedAt, // 분봉 시작시간(기준시간)
        BigDecimal openPrice, // 시가
        BigDecimal highPrice, // 고가
        BigDecimal lowPrice, // 저가
        BigDecimal closePrice, // 종가
        Long volume, // 거래량
        BigDecimal tradeAmount, // 거래대금
        boolean completed // 분봉이 확정되었는지 여부 (23분 23초라면 22분 분봉까지는 확정, 23분 분봉은 아직 확정이 아닌 상태)
) {
}
