package com.finmate.domain.stock.market;

import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public enum StockRealtimeMarketSession {
    // 마켓 세션명 관리
    NASDAQ_REGULAR("NASDAQ 정규장"),
    NASDAQ_PRE_MARKET("NASDAQ 프리마켓"),
    NASDAQ_AFTER_MARKET("NASDAQ 애프터마켓"),
    KRX_REGULAR("KRX 정규장"),
    KRX_AFTER_HOURS("KRX 시간외시장"),
    NXT_PRE_MARKET("NXT 프리마켓"),
    KRX_NXT_MAIN("KRX/NXT 메인마켓"),
    KRX_NXT_AFTER_MARKET("KRX/NXT 시간외시장"),
    NXT_AFTER_MARKET("NXT 애프터마켓"),
    UNKNOWN("장 구분 확인 불가");

    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private final String displayName;

    StockRealtimeMarketSession(String displayName) {
        this.displayName = displayName;
    }

    public static StockRealtimeMarketSession resolve(KisRealtimeApi api, Map<String, String> values) {
        if (api == KisRealtimeApi.OVERSEAS_STOCK_TRADE) {
            StockRealtimeMarketSession session = switch (normalize(values.get("MTYP"))) {
                case "1" -> NASDAQ_REGULAR;
                case "2" -> NASDAQ_PRE_MARKET;
                case "3" -> NASDAQ_AFTER_MARKET;
                default -> UNKNOWN;
            };
            if (session != UNKNOWN) {
                return session;
            }
            return resolveNasdaqTime(values.get("XHMS"));
        }
        if (api == KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK) {
            return resolveNasdaqTime(values.get("xhms"));
        }
        if (api != KisRealtimeApi.DOMESTIC_STOCK_TRADE
                && api != KisRealtimeApi.DOMESTIC_STOCK_ORDERBOOK) {
            return UNKNOWN;
        }

        LocalTime tradeTime = parseTime(api == KisRealtimeApi.DOMESTIC_STOCK_TRADE
                ? values.get("STCK_CNTG_HOUR")
                : values.get("BSOP_HOUR"));
        if (tradeTime == null) {
            return UNKNOWN;
        }
        if (between(tradeTime, LocalTime.of(8, 0), LocalTime.of(8, 50))) {
            return NXT_PRE_MARKET;
        }
        if (between(tradeTime, LocalTime.of(9, 0, 30), LocalTime.of(15, 20))) {
            return KRX_NXT_MAIN;
        }
        if (between(tradeTime, LocalTime.of(9, 0), LocalTime.of(15, 30))) {
            return KRX_REGULAR;
        }
        if (between(tradeTime, LocalTime.of(15, 40), LocalTime.of(18, 0))) {
            return KRX_NXT_AFTER_MARKET;
        }
        if (between(tradeTime, LocalTime.of(18, 0), LocalTime.of(20, 0))) {
            return NXT_AFTER_MARKET;
        }
        return UNKNOWN;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTradingSession() {
        return this != UNKNOWN;
    }

    private static StockRealtimeMarketSession resolveNasdaqTime(String rawTime) {
        LocalTime time = parseTime(rawTime);
        if (time == null) {
            return UNKNOWN;
        }
        if (between(time, LocalTime.of(4, 0), LocalTime.of(9, 30))) {
            return NASDAQ_PRE_MARKET;
        }
        if (between(time, LocalTime.of(9, 30), LocalTime.of(16, 0))) {
            return NASDAQ_REGULAR;
        }
        if (between(time, LocalTime.of(16, 0), LocalTime.of(20, 0))) {
            return NASDAQ_AFTER_MARKET;
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(normalize(value), TRADE_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean between(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
