package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeAmount;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeQuantity;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validateOrderPrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositivePrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositiveQuantity;

// 예약 주문용 엔티티
// 이는 예약 조건이 만족되면 나중에 실제 주문을 만들기 위한 예약 데이터를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_order_reservation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_order_reservation_number",
                        columnNames = "reservation_number"
                )
        }
)
@Entity
public class StockOrderReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_number", nullable = false, length = 36, updatable = false)
    private String reservationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockOrderSide side; // 매수 예약 or 매도 예약인지

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private StockOrderType orderType; // 지정가 주문 or 시장가 주문

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_condition", nullable = false, length = 30)
    private StockOrderTriggerCondition triggerCondition; // 예약 주문 실행 조건

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockOrderReservationStatus status; // 예약 주문의 상태

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity; // 예약 수량

    @Column(name = "trigger_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal triggerPrice; // 예약 조건 가격

    @Column(name = "order_price", precision = 19, scale = 6)
    private BigDecimal orderPrice; // 주문 가격

    @Column(name = "reserved_cash_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedCashAmount = BigDecimal.ZERO; // 예약된 금액

    @Column(name = "reserved_stock_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal reservedStockQuantity = BigDecimal.ZERO; // 예약된 수량

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 예약주문 만료날짜

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt; // 예약주문 접수날짜

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockOrderReservation create(String reservationNumber,
                                               Investment investment,
                                               Stock stock,
                                               StockOrderSide side,
                                               StockOrderType orderType,
                                               StockOrderTriggerCondition triggerCondition,
                                               CurrencyCode currencyCode,
                                               BigDecimal quantity,
                                               BigDecimal triggerPrice,
                                               BigDecimal orderPrice,
                                               BigDecimal reservedCashAmount,
                                               BigDecimal reservedStockQuantity,
                                               LocalDateTime expiresAt) {
        validateRequired(reservationNumber, "예약번호는 필수입니다.");
        validateRequired(investment, "증권 계좌는 필수입니다.");
        validateRequired(stock, "종목은 필수입니다.");
        validateRequired(side, "매수/매도 구분은 필수입니다.");
        validateRequired(orderType, "주문 유형은 필수입니다.");
        validateRequired(triggerCondition, "예약 조건은 필수입니다.");
        validateRequired(currencyCode, "통화는 필수입니다.");
        validatePositiveQuantity(quantity);
        validatePositivePrice(currencyCode, triggerPrice, "예약 기준 가격은 0보다 커야 합니다.");
        validateOrderPrice(orderType, currencyCode, orderPrice);

        StockOrderReservation reservation = new StockOrderReservation();
        reservation.reservationNumber = reservationNumber;
        reservation.investment = investment;
        reservation.stock = stock;
        reservation.side = side;
        reservation.orderType = orderType;
        reservation.triggerCondition = triggerCondition;
        reservation.status = StockOrderReservationStatus.ACTIVE;
        reservation.currencyCode = currencyCode;
        reservation.quantity = quantity;
        reservation.triggerPrice = triggerPrice;
        reservation.orderPrice = orderPrice;
        reservation.reservedCashAmount = normalizeAmount(reservedCashAmount);
        reservation.reservedStockQuantity = normalizeQuantity(reservedStockQuantity);
        reservation.expiresAt = expiresAt;
        return reservation;
    }

    // 예약 조건이 만족되었는지
    public boolean isTriggerSatisfied(BigDecimal currentPrice) {
        validateRequired(currentPrice, "현재가는 필수입니다.");
        return this.status == StockOrderReservationStatus.ACTIVE
                && !isExpired(LocalDateTime.now())
                && this.triggerCondition.isSatisfied(currentPrice, this.triggerPrice);
    }

    // 예약주문이 만료되었는지
    public boolean isExpired(LocalDateTime now) {
        validateRequired(now, "현재 시각은 필수입니다.");
        return this.expiresAt != null && !this.expiresAt.isAfter(now);
    }

    // 조건을 만족하여 예약 주문을 실제 주문으로 전환
    public void markTriggered(LocalDateTime triggeredAt) {
        if (this.status != StockOrderReservationStatus.ACTIVE) {
            throw new RuntimeException("활성 예약만 실행할 수 있습니다.");
        }

        validateRequired(triggeredAt, "예약 실행 시각은 필수입니다.");

        this.triggeredAt = triggeredAt;
        this.status = StockOrderReservationStatus.TRIGGERED;
    }

    // 예약 주문
    public void cancel() {
        if (this.status != StockOrderReservationStatus.ACTIVE) {
            throw new RuntimeException("활성 예약만 취소할 수 있습니다.");
        }

        this.status = StockOrderReservationStatus.CANCELED;
    }

    // 예약 주문 만료
    public void expire() {
        if (this.status != StockOrderReservationStatus.ACTIVE) {
            throw new RuntimeException("활성 예약만 만료 처리할 수 있습니다.");
        }

        this.status = StockOrderReservationStatus.EXPIRED;
    }

    // 예약 주문 실패
    public void fail() {
        if (this.status != StockOrderReservationStatus.ACTIVE) {
            throw new RuntimeException("활성 예약만 실패 처리할 수 있습니다.");
        }

        this.status = StockOrderReservationStatus.FAILED;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
