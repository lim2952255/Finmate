package com.finmate.infra.kis.stock.realtime;

// KIS 실시간 데이터가 수신되었다는 사실 Spring 내부에 알리기 위한 이벤트 객체
public record KisRealtimePayloadReceivedEvent(
        KisRealtimePayload payload
) {
}
