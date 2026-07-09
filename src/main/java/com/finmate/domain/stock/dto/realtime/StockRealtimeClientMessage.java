package com.finmate.domain.stock.dto.realtime;

import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 서버가 클라이언트에게 전달하는 실시간 주식 시세 DTO
public record StockRealtimeClientMessage(
        String type,
        Long stockId,
        KisRealtimeApi api,
        String trId,
        String trKey,
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
        String candleType,
        LocalDateTime receivedAt
) {
}
