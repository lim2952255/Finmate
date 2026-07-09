package com.finmate.domain.stock.trading.event;

// 주문이 접수(활성화)되었을 때 발생하는 이벤트 (종목을 구독하기 위해 발생하는 이벤트)
public record StockOrderActivatedEvent(
        Long stockId
) {
}
