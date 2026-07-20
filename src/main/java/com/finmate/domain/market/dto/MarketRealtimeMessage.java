package com.finmate.domain.market.dto;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
// 실시간 주가지수 정보를 담는 레코드
public record MarketRealtimeMessage(
        String type,
        MarketIndicatorSymbol indicator,
        MarketIndicatorType indicatorType,
        String displayName,
        String nameKo,
        String unit,
        int fractionDigits,
        String source,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal change,
        BigDecimal changeRate,
        String changeSign,
        Long accumulatedVolume,
        BigDecimal accumulatedTradeAmount,
        String tradeDate,
        String tradeTime,
        String priceChangeClass,
        LocalDateTime receivedAt
) {
}
