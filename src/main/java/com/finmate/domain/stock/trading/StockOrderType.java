package com.finmate.domain.stock.trading;

// 주문 방식
public enum StockOrderType {
    MARKET, // 시장가 주문 (현재 시장에서 바로 체결가능한 가격으로 주문)
    LIMIT // 지정가 주문 (예약 주문)
}
