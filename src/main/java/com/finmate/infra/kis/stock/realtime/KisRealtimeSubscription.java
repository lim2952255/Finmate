package com.finmate.infra.kis.stock.realtime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// Kis WebSocket에서 어떤 실시간 데이터를 구독할지 표현하는 객체
public record KisRealtimeSubscription(
        KisRealtimeApi api, // 어떤 실시간 데이터를 구독할지
        String trKey // 어떤 종목 / 지수를 구독할지
) {
    public KisRealtimeSubscription {
        validateRequired(api, "KIS realtime api is required.");
        validateRequired(trKey, "KIS realtime trKey is required.");
        trKey = trKey.trim();
    }

    public String id() {
        return api.getTrId() + ":" + trKey;
    }
}
