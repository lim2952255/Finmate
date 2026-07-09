package com.finmate.domain.stock.trading;

// 주식 매수/매도 예약 상태
public enum StockOrderReservationStatus {
    ACTIVE, // 아직 살아있는 예약 주문
    TRIGGERED, // 조건이 충족되어 실제 주문으로 전환된 예약
    CANCELED, // 사용자가 직접 취소한 예약
    EXPIRED, // 유효기간이 지나 조건 미충족으로 만료된 예약
    FAILED // 조건은 충족됐지만 주문 생성/체결에 실패한 예약
}
