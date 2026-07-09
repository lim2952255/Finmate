package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 사용자 주식 주문 요청용 dto
@Getter
@Setter
public class StockOrderRequest {
    private Long investmentId;
    private Long stockId;
    private StockOrderSide side = StockOrderSide.BUY;
    private StockOrderType orderType = StockOrderType.MARKET;
    private BigDecimal quantity;
    private BigDecimal orderPrice;

    // 지정가 주문 만료기한
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;
}
