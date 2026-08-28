package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.domain.stock.trading.StockOrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 사용자 예약주문 요청용 dto
@Getter
@Setter
public class StockOrderReservationRequest {
    @NotNull(message = "증권 계좌는 필수입니다.")
    private Long investmentId;
    @NotNull(message = "종목은 필수입니다.")
    private Long stockId;
    @NotNull(message = "매수/매도 구분은 필수입니다.")
    private StockOrderSide side = StockOrderSide.BUY;
    @NotNull(message = "주문 유형은 필수입니다.")
    private StockOrderType orderType = StockOrderType.MARKET;
    @NotNull(message = "실행 조건은 필수입니다.")
    private StockOrderTriggerCondition triggerCondition = StockOrderTriggerCondition.PRICE_AT_OR_BELOW;
    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 0보다 커야 합니다.")
    private BigDecimal quantity;
    @NotNull(message = "조건 가격은 필수입니다.")
    @Positive(message = "조건 가격은 0보다 커야 합니다.")
    private BigDecimal triggerPrice;
    private BigDecimal orderPrice;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @NotNull(message = "예약 주문 만료시각은 필수입니다.")
    private LocalDateTime expiresAt;
}
