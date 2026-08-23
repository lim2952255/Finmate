package com.finmate.domain.stock.dto.detail;

import java.time.LocalDateTime;
import java.util.List;

// Redis에 저장할 분봉 목록과 KIS API로부터 데이터를 조회한 시각을 담는 레코드
public record StockMinuteChartCache(
        LocalDateTime fetchedAt, // KIS API로부터 분봉 데이터를 받아온 시간
        List<StockMinuteCandleData> candles // Redis에 캐싱할 분봉데이터목록
) {
    public StockMinuteChartCache {
        candles = candles == null ? List.of() : List.copyOf(candles);
    }
}
