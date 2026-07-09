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
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositivePrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositiveQuantity;

// 증권 계좌별 보유 종목, 보유 수량, 매도 잠금 수량, 평균 매수가를 저장하는 엔티티
// 각 계좌가 보유중인 종목과 수량을 관리하며, 매도 주문에 의해 종목 수량에 대해 lock을 걸거나 해제하는 로직을 담당한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_holding",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_holding_investment_stock",
                        columnNames = {"investment_id", "stock_id"}
                )
        }
)
@Entity
public class StockHolding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO; // 종목 보유 수량

    // 매도 주문이 걸려있어 락이 걸려있는 종목의 수량
    @Column(name = "locked_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal lockedQuantity = BigDecimal.ZERO;

    @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal averagePurchasePrice = BigDecimal.ZERO; // 평균단가

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockHolding create(Investment investment, Stock stock, CurrencyCode currencyCode) {
        validateRequired(investment, "증권 계좌는 필수입니다.");
        validateRequired(stock, "종목은 필수입니다.");
        validateRequired(currencyCode, "통화는 필수입니다.");

        StockHolding holding = new StockHolding();
        holding.investment = investment;
        holding.stock = stock;
        holding.currencyCode = currencyCode;
        holding.quantity = BigDecimal.ZERO;
        holding.lockedQuantity = BigDecimal.ZERO;
        holding.averagePurchasePrice = BigDecimal.ZERO;
        return holding;
    }

    // 매도 가능 수량 계산 (전체 보유 수량 - 매도 주문에 잠겨있는 수량)
    public BigDecimal getAvailableQuantity() {
        return this.quantity.subtract(this.lockedQuantity);
    }

    // 매도 주문을 생성할 때, 보유중인 종목의 수량에서 해당 수량만큼 잠금을 수행하는 메서드
    public void lockQuantity(BigDecimal quantity) {
        validatePositiveQuantity(quantity);

        if (getAvailableQuantity().compareTo(quantity) < 0) {
            throw new RuntimeException("매도 가능 수량이 부족합니다.");
        }

        this.lockedQuantity = this.lockedQuantity.add(quantity);
    }

    // 매도 주문이 최소되었거단 만료되었을 때, 락으로 걸려있는 종목들의 락을 해제하는 매서드
    public void releaseLockedQuantity(BigDecimal quantity) {
        validatePositiveQuantity(quantity);

        if (this.lockedQuantity.compareTo(quantity) < 0) {
            throw new RuntimeException("잠금 수량이 부족합니다.");
        }

        this.lockedQuantity = this.lockedQuantity.subtract(quantity);
    }

    // 매수 주문이 체결되었을 때 수량과 평균단가를 반영하는 메서드
    public void applyBuyExecution(BigDecimal executedQuantity, BigDecimal executedPrice) {
        validatePositiveQuantity(executedQuantity);
        validatePositivePrice(this.currencyCode, executedPrice, "가격은 0보다 커야 합니다.");

        BigDecimal currentCost = this.quantity.multiply(this.averagePurchasePrice);
        BigDecimal executionCost = executedQuantity.multiply(executedPrice);
        BigDecimal nextQuantity = this.quantity.add(executedQuantity);

        this.quantity = nextQuantity;
        this.averagePurchasePrice = currentCost.add(executionCost)
                .divide(nextQuantity, 6, RoundingMode.HALF_UP);
    }

    // 매도 주문이 체결되었을 때 수량과 평균단가를 반영하는 메서드
    public void applySellExecution(BigDecimal executedQuantity) {
        validatePositiveQuantity(executedQuantity);

        if (this.lockedQuantity.compareTo(executedQuantity) < 0) {
            throw new RuntimeException("잠금 수량이 부족합니다.");
        }

        this.lockedQuantity = this.lockedQuantity.subtract(executedQuantity);
        this.quantity = this.quantity.subtract(executedQuantity);

        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePurchasePrice = BigDecimal.ZERO;
        }
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
