package com.finmate.service.stock.realtime;

// 클라이언트가 특정 종목을 구독하고 있는 목적
public enum StockRealtimeSubscriptionPurpose {
    DETAIL_PAGE, // 종목 상세페이지를 조회중
    ORDER_PAGE, // 주문 페이지에서 종목의 체결가와 호가를 조회중
    PORTFOLIO_PAGE, // 포트폴리오 페이지를 조회중
    ACTIVE_ORDER, // 지정가 주문이 접수된 상태
    ACTIVE_RESERVATION // 예약 주문이 접수된 상태
}
