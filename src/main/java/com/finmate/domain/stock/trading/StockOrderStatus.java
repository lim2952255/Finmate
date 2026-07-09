package com.finmate.domain.stock.trading;

// 실제 주문이 처리되고 체결되는 과정
public enum StockOrderStatus {
    PENDING, // 주문 생성 상태, 아직 주문 처리 전
    SUBMITTED, // 주문이 제출된 상태, 아직 주문이 체결되기를 기다리는 상태
    PARTIALLY_FILLED, // 주문이 일부만 체결된 상태(10주 중 5주 등등)
    FILLED, // 전량 체결이 완료된 상태
    CANCELED, // 사용자가 주문을 취소한 상태
    REJECTED, //주문이 거절된 상태
    EXPIRED // 주문 유효기간이 만료된 상태
}
