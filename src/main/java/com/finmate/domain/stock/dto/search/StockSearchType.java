package com.finmate.domain.stock.dto.search;

public enum StockSearchType {
    STOCK("종목", "종목명 또는 종목코드"),
    INDUSTRY("업종", "업종명 또는 업종코드");

    private final String displayName;
    private final String placeholder;

    StockSearchType(String displayName, String placeholder) {
        this.displayName = displayName;
        this.placeholder = placeholder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
