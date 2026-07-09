package com.finmate.service.stock.trading;

import com.finmate.domain.stock.Stock;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayload;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayloadReceivedEvent;
import com.finmate.repository.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 실시간 시세가 들어올 때마다, 미체결 주문 / 예약 주문을 처리하도록 연결해주는 이벤트 리스너
// KIS WebSocket에 실시간 시세가 들어올 때 마다, KisRealtimeWebSocketClinet가 이벤트를 발생시키고,
// 이때 KisRealtimePayloadReceivedEvent가 발생하고, 이를 해당 이벤트리스너가 받아서 미체결 주문 / 예약 주문을 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StockTradingRealtimeExecutionListener {
    private final StockRepository stockRepository;
    private final StockTradingExecutionService stockTradingExecutionService; // 실시간 시세 기반 주문 체결 서비스

    // KisRealtimePayloadReceivedEvent가 발생하면 실행되는 이벤트리스너
    @EventListener
    public void handleRealtimePayload(KisRealtimePayloadReceivedEvent event) {
        KisRealtimePayload payload = event.payload();
        if (payload == null || payload.trKey() == null || payload.trKey().isBlank()) {
            return;
        }
        // 주식 체결 / 호가 데이터만 처리
        if (!payload.api().isOrderbook() && !payload.api().name().endsWith("_STOCK_TRADE")) {
            return;
        }

        // 실시간으로 받은 종목을 찾은 다음, 주문 처리 로직 수행
        stockRepository.findByRealtimeKey(payload.trKey())
                .map(Stock::getId)
                .ifPresent(stockId -> {
                    try {
                        // 종목의 실시간 체결가 및 호가를 받으면, 이 데이터를 기반으로 주문을 체결할지 말지 여부를 결정하고, 주문을 체결하는 메서드
                        stockTradingExecutionService.processRealtimeUpdate(stockId);
                    } catch (RuntimeException e) {
                        log.warn("실시간 시세 기반 주문 처리에 실패했습니다. stockId={}", stockId, e);
                    }
                });
    }
}
