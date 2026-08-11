package com.finmate.domain.stock.metadata.domestic;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 국내 주식의 일별 대차거래(주식 대여/상환) 데이터를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "domestic_stock_loan_transaction_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_domestic_stock_loan_transaction_daily_key",
                columnNames = {"stock_id", "trade_date"}))
@Entity
public class DomesticStockLoanTransactionDaily {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;
    @Column(name = "trade_date", nullable = false, updatable = false)
    private LocalDate tradeDate;
    @Column(name = "close_price", precision = 19, scale = 6)
    private BigDecimal closePrice; // 종가
    @Column(name = "new_loan_quantity")
    private Long newLoanQuantity; // 신규 대차 수량: 새롭게 빌려간 주식 수량
    @Column(name = "redeemed_loan_quantity")
    private Long redeemedLoanQuantity; // 대차 상환 수량: 기존에 빌렸던 주식 중 돌려받은 수량
    @Column(name = "loan_balance_quantity")
    private Long loanBalanceQuantity; // 대차 잔고 수량: 현재까지 빌려간 뒤, 아직 상환하지 않은 주식 수량
    @Column(name = "new_loan_amount", precision = 24, scale = 4)
    private BigDecimal newLoanAmount; // 신규 대차 금액: 새롭게 빌려간 주식 금액
    @Column(name = "redeemed_loan_amount", precision = 24, scale = 4)
    private BigDecimal redeemedLoanAmount; // 대차 상환 금액: 기존에 빌렸던 주식 중 돌려받은 금액
    @Column(name = "loan_balance_amount", precision = 24, scale = 4)
    private BigDecimal loanBalanceAmount; // 대차 잔고 금액: 현재까지 빌려간 뒤, 아직 상환하지 않은 금액
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockLoanTransactionDaily create(Stock stock, LocalDate tradeDate,
            BigDecimal closePrice, Long newLoanQuantity, Long redeemedLoanQuantity,
            Long loanBalanceQuantity, BigDecimal newLoanAmount, BigDecimal redeemedLoanAmount,
            BigDecimal loanBalanceAmount) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(tradeDate, "거래일은 필수입니다.");
        DomesticStockLoanTransactionDaily daily = new DomesticStockLoanTransactionDaily();
        daily.stock = stock;
        daily.tradeDate = tradeDate;
        daily.apply(closePrice, newLoanQuantity, redeemedLoanQuantity, loanBalanceQuantity,
                newLoanAmount, redeemedLoanAmount, loanBalanceAmount);
        return daily;
    }

    public void update(BigDecimal closePrice, Long newLoanQuantity, Long redeemedLoanQuantity,
            Long loanBalanceQuantity, BigDecimal newLoanAmount, BigDecimal redeemedLoanAmount,
            BigDecimal loanBalanceAmount) {
        apply(closePrice, newLoanQuantity, redeemedLoanQuantity, loanBalanceQuantity,
                newLoanAmount, redeemedLoanAmount, loanBalanceAmount);
    }

    private void apply(BigDecimal closePrice, Long newLoanQuantity, Long redeemedLoanQuantity,
            Long loanBalanceQuantity, BigDecimal newLoanAmount, BigDecimal redeemedLoanAmount,
            BigDecimal loanBalanceAmount) {
        this.closePrice = closePrice;
        this.newLoanQuantity = newLoanQuantity;
        this.redeemedLoanQuantity = redeemedLoanQuantity;
        this.loanBalanceQuantity = loanBalanceQuantity;
        this.newLoanAmount = newLoanAmount;
        this.redeemedLoanAmount = redeemedLoanAmount;
        this.loanBalanceAmount = loanBalanceAmount;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist void prePersist() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
