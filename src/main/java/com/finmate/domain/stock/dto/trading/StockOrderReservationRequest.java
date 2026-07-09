package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.domain.stock.trading.StockOrderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 사용자 예약주문 요청용 dto
@Getter
@Setter
public class StockOrderReservationRequest {
    private Long investmentId;
    private Long stockId;
    private StockOrderSide side = StockOrderSide.BUY;
    private StockOrderType orderType = StockOrderType.MARKET;
    private StockOrderTriggerCondition triggerCondition = StockOrderTriggerCondition.PRICE_AT_OR_BELOW;
    private BigDecimal quantity;
    private BigDecimal triggerPrice;
    private BigDecimal orderPrice;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;
}
