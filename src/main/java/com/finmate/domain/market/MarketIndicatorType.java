package com.finmate.domain.market;

// 환율인지, 주가지수인지 구분하는 큰 카테고리
public enum MarketIndicatorType {
    EXCHANGE_RATE("환율"),
    STOCK_INDEX("주가지수");

    private final String displayName;

    MarketIndicatorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
