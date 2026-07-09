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
import static com.finmate.domain.stock.trading.TradingAmountValidator.validateCurrencyAmounts;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositivePrice;
import static com.finmate.domain.stock.trading.TradingAmountValidator.validatePositiveQuantity;

// 주식거래가 실제 체결되었을 때 생성되는 기록을 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_trade_transaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_trade_transaction_execution_id",
                        columnNames = "external_execution_id"
                )
        }
)
@Entity
public class StockTradeTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StockOrder order;

    @Column(name = "external_execution_id", length = 80)
    private String externalExecutionId; // 주문 체결 고유번호

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockOrderSide side; // 매수 체결인지 / 매도 체결인지

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity; // 체결된 수량

    @Column(name = "execution_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal executionPrice; // 체결의 1주당 가격

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount; // 수수료와 세금을 빼기 전 순수 거래금액

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount; // 거래 수수료

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount; // 거래 세금

    @Column(name = "net_cash_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netCashAmount; // 실제 거래 정산액 (매수:grossAmount + commissionAmount, 매도: grossAmount - commissionAmount - taxAmount)

    @Column(name = "cash_balance_before_transaction", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalanceBeforeTransaction; // 체결 전 예수금

    @Column(name = "cash_balance_after_transaction", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalanceAfterTransaction; // 체결 후 예수금

    @Column(name = "holding_quantity_before_transaction", nullable = false, precision = 19, scale = 6)
    private BigDecimal holdingQuantityBeforeTransaction; // 체결 전 보유 주식 수량

    @Column(name = "holding_quantity_after_transaction", nullable = false, precision = 19, scale = 6)
    private BigDecimal holdingQuantityAfterTransaction; // 체결 후 보유 주식 수량

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static StockTradeTransaction create(Investment investment,
                                               Stock stock,
                                               StockOrder order,
                                               String externalExecutionId,
                                               StockOrderSide side,
                                               CurrencyCode currencyCode,
                                               BigDecimal quantity,
                                               BigDecimal executionPrice,
                                               BigDecimal grossAmount,
                                               BigDecimal commissionAmount,
                                               BigDecimal taxAmount,
                                               BigDecimal netCashAmount,
                                               BigDecimal cashBalanceBeforeTransaction,
                                               BigDecimal cashBalanceAfterTransaction,
                                               BigDecimal holdingQuantityBeforeTransaction,
                                               BigDecimal holdingQuantityAfterTransaction,
                                               LocalDateTime executedAt) {
        validateRequired(investment, "증권 계좌는 필수입니다.");
        validateRequired(stock, "종목은 필수입니다.");
        validateRequired(order, "주문은 필수입니다.");
        validateRequired(side, "매수/매도 구분은 필수입니다.");
        validateRequired(currencyCode, "통화는 필수입니다.");
        validateRequired(executedAt, "체결 시각은 필수입니다.");
        validatePositiveQuantity(quantity);
        validatePositivePrice(currencyCode, executionPrice, "체결 가격은 0보다 커야 합니다.");
        validateRequired(grossAmount, "거래금액은 필수입니다.");
        validateRequired(commissionAmount, "수수료는 필수입니다.");
        validateRequired(taxAmount, "세금은 필수입니다.");
        validateRequired(netCashAmount, "현금 정산액은 필수입니다.");
        validateRequired(cashBalanceBeforeTransaction, "거래 전 예수금은 필수입니다.");
        validateRequired(cashBalanceAfterTransaction, "거래 후 예수금은 필수입니다.");
        validateRequired(holdingQuantityBeforeTransaction, "거래 전 보유수량은 필수입니다.");
        validateRequired(holdingQuantityAfterTransaction, "거래 후 보유수량은 필수입니다.");
        validateCurrencyAmounts(
                currencyCode,
                executionPrice,
                grossAmount,
                commissionAmount,
                taxAmount,
                netCashAmount,
                cashBalanceBeforeTransaction,
                cashBalanceAfterTransaction);

        StockTradeTransaction transaction = new StockTradeTransaction();
        transaction.investment = investment;
        transaction.stock = stock;
        transaction.order = order;
        transaction.externalExecutionId = externalExecutionId;
        transaction.side = side;
        transaction.currencyCode = currencyCode;
        transaction.quantity = quantity;
        transaction.executionPrice = executionPrice;
        transaction.grossAmount = grossAmount;
        transaction.commissionAmount = commissionAmount;
        transaction.taxAmount = taxAmount;
        transaction.netCashAmount = netCashAmount;
        transaction.cashBalanceBeforeTransaction = cashBalanceBeforeTransaction;
        transaction.cashBalanceAfterTransaction = cashBalanceAfterTransaction;
        transaction.holdingQuantityBeforeTransaction = holdingQuantityBeforeTransaction;
        transaction.holdingQuantityAfterTransaction = holdingQuantityAfterTransaction;
        transaction.executedAt = executedAt;
        return transaction;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
