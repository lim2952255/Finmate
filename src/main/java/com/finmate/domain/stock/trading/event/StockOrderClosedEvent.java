package com.finmate.domain.stock.trading.event;

// 주문이 모두 체결 또는 종료되었을 때 발생하는 이벤트(구독을 해제하기 위해 발생하는 이벤트)
public record StockOrderClosedEvent(
        Long stockId
) {
}
