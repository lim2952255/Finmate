package com.finmate.infra.kis.stock.realtime;

// KIS 서버로부터 실시간 종목데이터를 수신한 경우, 해당 이벤트를 발생시켜 종목을 구독중인 클라이언트들에게 종목 데이터를 전달한다.
public record KisRealtimePayloadReceivedEvent(
        KisRealtimePayload payload
) {
}
