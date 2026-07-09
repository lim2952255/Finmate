package com.finmate.domain.stock.dto.realtime;

import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 서버가 클라이언트에게 전달하는 실시간 주식 호가 DTO
public record StockRealtimeOrderbookClientMessage(
        String type,
        Long stockId,
        KisRealtimeApi api,
        String trId,
        String trKey,
        List<OrderbookLevel> askLevels,
        List<OrderbookLevel> bidLevels,
        BigDecimal totalAskQuantity,
        BigDecimal totalBidQuantity,
        String quoteTime,
        LocalDateTime receivedAt
) {
    public record OrderbookLevel(
            int level,
            BigDecimal price,
            BigDecimal quantity
    ) {
    }
}
