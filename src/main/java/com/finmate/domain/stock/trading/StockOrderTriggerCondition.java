package com.finmate.domain.stock.trading;

import java.math.BigDecimal;

// 예약 주문의 조건
public enum StockOrderTriggerCondition {
    // 현재가가 지정가 이상이면 실행 (매도)
    PRICE_AT_OR_ABOVE {
        @Override
        public boolean isSatisfied(BigDecimal currentPrice, BigDecimal triggerPrice) {
            return currentPrice.compareTo(triggerPrice) >= 0;
        }
    },
    // 현재가가 지정가 이하이면 실행 (매수)
    PRICE_AT_OR_BELOW {
        @Override
        public boolean isSatisfied(BigDecimal currentPrice, BigDecimal triggerPrice) {
            return currentPrice.compareTo(triggerPrice) <= 0;
        }
    };

    public abstract boolean isSatisfied(BigDecimal currentPrice, BigDecimal triggerPrice);
}
