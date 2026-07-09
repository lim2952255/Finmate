package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeAmount;
import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeQuantity;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validateOrderExpiration;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validateOrderPrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositiveQuantity;

// 주식 매수/매도 주문 1건을 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_order",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_order_order_number",
                        columnNames = "order_number"
                )
        }
)
@Entity
public class StockOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 36, updatable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", unique = true)
    private StockOrderReservation reservation; // 예약 주문이 실제 주문으로 전환된 경우 연관관계를 설정한다.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockOrderSide side; // 주식 매수 or 주식 매도

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private StockOrderType orderType; // 시장가 주문 or 지정가 주문

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockOrderStatus status; // 주문 상태

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity; // 주문 수량

    @Column(name = "executed_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal executedQuantity = BigDecimal.ZERO; // 체결된 수량

    @Column(name = "canceled_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal canceledQuantity = BigDecimal.ZERO; // 취소된 수량

    @Column(name = "order_price", precision = 19, scale = 6)
    private BigDecimal orderPrice; // 주문 가격(지정가)

    @Column(name = "reserved_cash_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedCashAmount = BigDecimal.ZERO; // 예약 예수금 (매수 주문에 사용하기 위해 lock을 건 금액)

    @Column(name = "reserved_stock_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal reservedStockQuantity = BigDecimal.ZERO; // 예약 주식 수량 (매도 주문을 낼때 미리 묶어둔 수량)

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 지정가 주문 만료시각

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockOrder create(String orderNumber,
                                    Investment investment,
                                    Stock stock,
                                    StockOrderReservation reservation,
                                    StockOrderSide side,
                                    StockOrderType orderType,
                                    CurrencyCode currencyCode,
                                    BigDecimal quantity,
                                    BigDecimal orderPrice,
                                    BigDecimal reservedCashAmount,
                                    BigDecimal reservedStockQuantity,
                                    LocalDateTime expiresAt) {
        validateRequired(orderNumber, "주문번호는 필수입니다.");
        validateRequired(investment, "증권 계좌는 필수입니다.");
        validateRequired(stock, "종목은 필수입니다.");
        validateRequired(side, "매수/매도 구분은 필수입니다.");
        validateRequired(orderType, "주문 유형은 필수입니다.");
        validateRequired(currencyCode, "통화는 필수입니다.");
        validatePositiveQuantity(quantity);
        validateOrderPrice(orderType, currencyCode, orderPrice);
        validateOrderExpiration(orderType, expiresAt);

        StockOrder order = new StockOrder();
        order.orderNumber = orderNumber;
        order.investment = investment;
        order.stock = stock;
        order.reservation = reservation;
        order.side = side;
        order.orderType = orderType;
        order.status = StockOrderStatus.PENDING;
        order.currencyCode = currencyCode;
        order.quantity = quantity;
        order.orderPrice = orderPrice;
        order.reservedCashAmount = normalizeAmount(reservedCashAmount);
        order.reservedStockQuantity = normalizeQuantity(reservedStockQuantity);
        order.expiresAt = orderType == StockOrderType.MARKET ? null : expiresAt;
        return order;
    }

    // 남은 수량은 전체 수량 - 체결된 수량 - 취소된 수량
    public BigDecimal getRemainingQuantity() {
        return this.quantity
                .subtract(this.executedQuantity)
                .subtract(this.canceledQuantity);
    }

    public boolean isExpired(LocalDateTime now) {
        validateRequired(now, "현재 시각은 필수입니다.");
        return (this.status == StockOrderStatus.SUBMITTED || this.status == StockOrderStatus.PARTIALLY_FILLED)
                && this.expiresAt != null
                && !this.expiresAt.isAfter(now);
    }

    // 접수 대기중인 주문을 접수
    public void markSubmitted() {
        if (this.status != StockOrderStatus.PENDING) {
            throw new RuntimeException("접수 대기 주문만 제출할 수 있습니다.");
        }

        this.status = StockOrderStatus.SUBMITTED;
    }

    // 접수한 주문을 체결
    public void applyExecution(BigDecimal executedQuantity) {
        validatePositiveQuantity(executedQuantity);

        if (getRemainingQuantity().compareTo(executedQuantity) < 0) {
            throw new RuntimeException("주문 잔여 수량보다 체결 수량이 큽니다.");
        }

        this.executedQuantity = this.executedQuantity.add(executedQuantity);
        this.status = getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0
                ? StockOrderStatus.FILLED
                : StockOrderStatus.PARTIALLY_FILLED;
    }

    // 남은 주문 체결을 취소
    public void cancelRemaining() {
        BigDecimal remainingQuantity = getRemainingQuantity();
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.canceledQuantity = this.canceledQuantity.add(remainingQuantity);
        }

        this.status = StockOrderStatus.CANCELED;
    }

    public void expireRemaining() {
        if (this.status != StockOrderStatus.SUBMITTED && this.status != StockOrderStatus.PARTIALLY_FILLED) {
            throw new RuntimeException("접수 중인 주문만 만료 처리할 수 있습니다.");
        }

        BigDecimal remainingQuantity = getRemainingQuantity();
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.canceledQuantity = this.canceledQuantity.add(remainingQuantity);
        }

        this.status = StockOrderStatus.EXPIRED;
    }

    // 주문 거절
    public void reject() {
        this.status = StockOrderStatus.REJECTED;
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
