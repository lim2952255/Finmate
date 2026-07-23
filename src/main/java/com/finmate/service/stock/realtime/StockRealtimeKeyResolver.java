package com.finmate.service.stock.realtime;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 종목 마스터의 심볼을 KIS 실시간 구독/조회에서 공통으로 사용하는 키로 변환한다.
public final class StockRealtimeKeyResolver {
    private static final String NASDAQ_REALTIME_PREFIX = "DNAS";
    private static final String NASDAQ_MASTER_PREFIX = "NAS";

    private StockRealtimeKeyResolver() {
    }

    public static String resolve(Stock stock) {
        validateRequired(stock, "Stock is required.");

        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return resolveNasdaq(stock);
        }

        if (stock.getRealtimeSymbol() != null && !stock.getRealtimeSymbol().isBlank()) {
            return stock.getRealtimeSymbol().trim();
        }

        return stock.getSymbol().trim();
    }

    private static String resolveNasdaq(Stock stock) {
        if (stock.getRealtimeSymbol() != null && !stock.getRealtimeSymbol().isBlank()) {
            String realtimeSymbol = stock.getRealtimeSymbol().trim();
            if (realtimeSymbol.startsWith(NASDAQ_REALTIME_PREFIX)) {
                return realtimeSymbol;
            }
            if (realtimeSymbol.startsWith(NASDAQ_MASTER_PREFIX)) {
                return "D" + realtimeSymbol;
            }
        }

        return NASDAQ_REALTIME_PREFIX + stock.getSymbol().trim();
    }
}
