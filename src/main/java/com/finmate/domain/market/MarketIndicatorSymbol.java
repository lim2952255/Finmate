package com.finmate.domain.market;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// USD/KRW, 코스피, 코스닥, 나스닥 종합, 나스닥 100 같은 실제 조회 대상을 설정
public enum MarketIndicatorSymbol {

    USD_KRW(
            MarketIndicatorType.EXCHANGE_RATE,
            MarketIndicatorKisApiType.OVERSEAS_DAILY_CHART_PRICE,
            "X",
            "FX@KRW",
            "USD/KRW",
            "원/달러",
            "미국 달러 대비 원화 환율",
            "KRW",
            2),
    KOSPI(
            MarketIndicatorType.STOCK_INDEX,
            MarketIndicatorKisApiType.DOMESTIC_DAILY_INDEX_CHART_PRICE,
            "U",
            "0001",
            "KOSPI",
            "코스피",
            "국내 코스피 종합주가지수",
            "pt",
            2),
    KOSDAQ(
            MarketIndicatorType.STOCK_INDEX,
            MarketIndicatorKisApiType.DOMESTIC_DAILY_INDEX_CHART_PRICE,
            "U",
            "1001",
            "KOSDAQ",
            "코스닥",
            "국내 코스닥 종합주가지수",
            "pt",
            2),
    NASDAQ_COMPOSITE(
            MarketIndicatorType.STOCK_INDEX,
            MarketIndicatorKisApiType.OVERSEAS_DAILY_CHART_PRICE,
            "N",
            "COMP",
            "NASDAQ Composite",
            "나스닥 종합",
            "미국 나스닥 종합지수",
            "pt",
            2),
    NASDAQ_100(
            MarketIndicatorType.STOCK_INDEX,
            MarketIndicatorKisApiType.OVERSEAS_DAILY_CHART_PRICE,
            "N",
            "NDX",
            "NASDAQ 100",
            "나스닥 100",
            "미국 나스닥 100 지수",
            "pt",
            2);

    private final MarketIndicatorType type;
    private final MarketIndicatorKisApiType kisApiType;
    private final String kisMarketDivisionCode;
    private final String kisSymbol;
    private final String displayName;
    private final String nameKo;
    private final String description;
    private final String unit;
    private final int fractionDigits;

    MarketIndicatorSymbol(MarketIndicatorType type,
                          MarketIndicatorKisApiType kisApiType,
                          String kisMarketDivisionCode,
                          String kisSymbol,
                          String displayName,
                          String nameKo,
                          String description,
                          String unit,
                          int fractionDigits) {
        this.type = type;
        this.kisApiType = kisApiType;
        this.kisMarketDivisionCode = kisMarketDivisionCode;
        this.kisSymbol = kisSymbol;
        this.displayName = displayName;
        this.nameKo = nameKo;
        this.description = description;
        this.unit = unit;
        this.fractionDigits = fractionDigits;
    }

    public MarketIndicatorType getType() {
        return type;
    }

    public MarketIndicatorKisApiType getKisApiType() {
        return kisApiType;
    }

    public String getKisMarketDivisionCode() {
        return kisMarketDivisionCode;
    }

    public String getKisSymbol() {
        return kisSymbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public int getFractionDigits() {
        return fractionDigits;
    }

    public boolean isDomesticRealtimeIndex() {
        return this == KOSPI || this == KOSDAQ;
    }

    public boolean isPollingRealtimeIndicator() {
        return this == USD_KRW || this == NASDAQ_COMPOSITE || this == NASDAQ_100;
    }

    public String getRealtimeMode() {
        return isDomesticRealtimeIndex() ? "WEBSOCKET" : "POLLING";
    }

    public static List<MarketIndicatorSymbol> findByType(MarketIndicatorType type) {
        return Arrays.stream(values())
                .filter(symbol -> symbol.type == type)
                .toList();
    }

    public static Optional<MarketIndicatorSymbol> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(valueOf(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // 기본값 설정
    public static MarketIndicatorSymbol defaultFor(MarketIndicatorType type) {
        return switch (type) {
            case EXCHANGE_RATE -> USD_KRW;
            case STOCK_INDEX -> KOSPI;
        };
    }
}
