package com.finmate.domain.stock.dto.ranking;

// Ranking 종류를 저장하는 enum타입
public enum StockRankingType {
    VOLUME("거래량"),
    TRADE_AMOUNT("거래대금");

    private final String displayName;

    StockRankingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
