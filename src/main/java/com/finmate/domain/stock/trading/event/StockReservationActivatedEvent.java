package com.finmate.domain.stock.trading.event;

// 예약주문을 활성화할때 발생하는 이벤트(종목을 구독하기 위해 발생하는 이벤트)
public record StockReservationActivatedEvent(
        Long stockId
) {
}
