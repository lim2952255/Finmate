package com.finmate.domain.market.dto;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimeSubscription;

public record MarketRealtimeSubscriptionMessage(
        String type,
        MarketIndicatorSymbol indicator,
        KisRealtimeApi api,
        String trId,
        String trKey
) {
    public static MarketRealtimeSubscriptionMessage of(String type,
                                                       MarketIndicatorSymbol indicator,
                                                       KisRealtimeSubscription subscription) {
        return new MarketRealtimeSubscriptionMessage(
                type,
                indicator,
                subscription.api(),
                subscription.api().getTrId(),
                subscription.trKey());
    }
}
