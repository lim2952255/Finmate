package com.finmate.domain.stock.trading.event;

// 예약주문이 실제 주문 접수 또는 취소되어 더이상 활성화되어 있지 않을때 발생하는 이벤트(종목에 대한 구독을 해제하기 위해 발생하는 이벤트)
public record StockReservationClosedEvent(
        Long stockId
) {
}
