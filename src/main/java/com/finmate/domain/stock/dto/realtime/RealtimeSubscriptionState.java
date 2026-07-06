package com.finmate.domain.stock.dto.realtime;

import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;

// 구독중인 종목에 대한 정보를 담고 있는 dto
public record RealtimeSubscriptionState(
        KisRealtimeApi api,
        String trId,
        String trKey,
        int subscriberCount,
        boolean active
) {
}
