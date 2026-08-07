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

// 종목 장마감시에 저장할 현재가 상세 스냅샷을 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_current_quote",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_current_quote_stock",
                columnNames = "stock_id"
        )
)
@Entity
public class DomesticStockCurrentQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true, updatable = false)
    private Stock stock;

    @Column(name = "current_price", precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(name = "change_amount", precision = 19, scale = 6)
    private BigDecimal changeAmount;

    @Column(name = "change_sign", length = 2)
    private String changeSign;

    @Column(name = "change_rate", precision = 19, scale = 6)
    private BigDecimal changeRate;

    @Column(name = "open_price", precision = 19, scale = 6)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 19, scale = 6)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 19, scale = 6)
    private BigDecimal lowPrice;

    @Column(name = "accumulated_volume")
    private Long accumulatedVolume;

    @Column(name = "accumulated_trade_amount", precision = 24, scale = 4)
    private BigDecimal accumulatedTradeAmount;

    @Column(precision = 19, scale = 6)
    private BigDecimal per;

    @Column(precision = 19, scale = 6)
    private BigDecimal pbr;

    @Column(precision = 19, scale = 6)
    private BigDecimal eps;

    @Column(precision = 19, scale = 6)
    private BigDecimal bps;

    @Column(name = "market_cap", precision = 24, scale = 4)
    private BigDecimal marketCap;

    @Column(name = "listed_shares")
    private Long listedShares;

    @Column(name = "w52_high_price", precision = 19, scale = 6)
    private BigDecimal w52HighPrice;

    @Column(name = "w52_high_date")
    private LocalDate w52HighDate;

    @Column(name = "w52_high_rate", precision = 19, scale = 6)
    private BigDecimal w52HighRate;

    @Column(name = "w52_low_price", precision = 19, scale = 6)
    private BigDecimal w52LowPrice;

    @Column(name = "w52_low_date")
    private LocalDate w52LowDate;

    @Column(name = "w52_low_rate", precision = 19, scale = 6)
    private BigDecimal w52LowRate;

    @Column(name = "foreign_holding_quantity")
    private Long foreignHoldingQuantity;

    @Column(name = "foreign_exhaustion_rate", precision = 19, scale = 6)
    private BigDecimal foreignExhaustionRate;

    @Column(name = "foreign_net_buy_quantity")
    private Long foreignNetBuyQuantity;

    @Column(name = "total_loan_balance_rate", precision = 19, scale = 6)
    private BigDecimal totalLoanBalanceRate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockCurrentQuote create(Stock stock,
                                                    BigDecimal currentPrice,
                                                    BigDecimal changeAmount,
                                                    String changeSign,
                                                    BigDecimal changeRate,
                                                    BigDecimal openPrice,
                                                    BigDecimal highPrice,
                                                    BigDecimal lowPrice,
                                                    Long accumulatedVolume,
                                                    BigDecimal accumulatedTradeAmount,
                                                    BigDecimal per,
                                                    BigDecimal pbr,
                                                    BigDecimal eps,
                                                    BigDecimal bps,
                                                    BigDecimal marketCap,
                                                    Long listedShares,
                                                    BigDecimal w52HighPrice,
                                                    LocalDate w52HighDate,
                                                    BigDecimal w52HighRate,
                                                    BigDecimal w52LowPrice,
                                                    LocalDate w52LowDate,
                                                    BigDecimal w52LowRate,
                                                    Long foreignHoldingQuantity,
                                                    BigDecimal foreignExhaustionRate,
                                                    Long foreignNetBuyQuantity,
                                                    BigDecimal totalLoanBalanceRate) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        DomesticStockCurrentQuote quote = new DomesticStockCurrentQuote();
        quote.stock = stock;
        quote.apply(currentPrice, changeAmount, changeSign, changeRate, openPrice, highPrice, lowPrice,
                accumulatedVolume, accumulatedTradeAmount, per, pbr, eps, bps, marketCap, listedShares,
                w52HighPrice, w52HighDate, w52HighRate, w52LowPrice, w52LowDate, w52LowRate,
                foreignHoldingQuantity, foreignExhaustionRate, foreignNetBuyQuantity, totalLoanBalanceRate);
        return quote;
    }

    public void update(BigDecimal currentPrice,
                       BigDecimal changeAmount,
                       String changeSign,
                       BigDecimal changeRate,
                       BigDecimal openPrice,
                       BigDecimal highPrice,
                       BigDecimal lowPrice,
                       Long accumulatedVolume,
                       BigDecimal accumulatedTradeAmount,
                       BigDecimal per,
                       BigDecimal pbr,
                       BigDecimal eps,
                       BigDecimal bps,
                       BigDecimal marketCap,
                       Long listedShares,
                       BigDecimal w52HighPrice,
                       LocalDate w52HighDate,
                       BigDecimal w52HighRate,
                       BigDecimal w52LowPrice,
                       LocalDate w52LowDate,
                       BigDecimal w52LowRate,
                       Long foreignHoldingQuantity,
                       BigDecimal foreignExhaustionRate,
                       Long foreignNetBuyQuantity,
                       BigDecimal totalLoanBalanceRate) {
        apply(currentPrice, changeAmount, changeSign, changeRate, openPrice, highPrice, lowPrice,
                accumulatedVolume, accumulatedTradeAmount, per, pbr, eps, bps, marketCap, listedShares,
                w52HighPrice, w52HighDate, w52HighRate, w52LowPrice, w52LowDate, w52LowRate,
                foreignHoldingQuantity, foreignExhaustionRate, foreignNetBuyQuantity, totalLoanBalanceRate);
        this.updatedAt = LocalDateTime.now();
    }

    private void apply(BigDecimal currentPrice,
                       BigDecimal changeAmount,
                       String changeSign,
                       BigDecimal changeRate,
                       BigDecimal openPrice,
                       BigDecimal highPrice,
                       BigDecimal lowPrice,
                       Long accumulatedVolume,
                       BigDecimal accumulatedTradeAmount,
                       BigDecimal per,
                       BigDecimal pbr,
                       BigDecimal eps,
                       BigDecimal bps,
                       BigDecimal marketCap,
                       Long listedShares,
                       BigDecimal w52HighPrice,
                       LocalDate w52HighDate,
                       BigDecimal w52HighRate,
                       BigDecimal w52LowPrice,
                       LocalDate w52LowDate,
                       BigDecimal w52LowRate,
                       Long foreignHoldingQuantity,
                       BigDecimal foreignExhaustionRate,
                       Long foreignNetBuyQuantity,
                       BigDecimal totalLoanBalanceRate) {
        this.currentPrice = currentPrice;
        this.changeAmount = changeAmount;
        this.changeSign = changeSign;
        this.changeRate = changeRate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.accumulatedVolume = accumulatedVolume;
        this.accumulatedTradeAmount = accumulatedTradeAmount;
        this.per = per;
        this.pbr = pbr;
        this.eps = eps;
        this.bps = bps;
        this.marketCap = marketCap;
        this.listedShares = listedShares;
        this.w52HighPrice = w52HighPrice;
        this.w52HighDate = w52HighDate;
        this.w52HighRate = w52HighRate;
        this.w52LowPrice = w52LowPrice;
        this.w52LowDate = w52LowDate;
        this.w52LowRate = w52LowRate;
        this.foreignHoldingQuantity = foreignHoldingQuantity;
        this.foreignExhaustionRate = foreignExhaustionRate;
        this.foreignNetBuyQuantity = foreignNetBuyQuantity;
        this.totalLoanBalanceRate = totalLoanBalanceRate;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
