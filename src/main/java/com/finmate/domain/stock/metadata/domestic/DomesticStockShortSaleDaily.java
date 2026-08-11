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

// 국내 주식의 일별 공매 거래 데이터를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "domestic_stock_short_sale_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_domestic_stock_short_sale_daily_key",
                columnNames = {"stock_id", "trade_date"}))
@Entity
public class DomesticStockShortSaleDaily {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;
    @Column(name = "trade_date", nullable = false, updatable = false)
    private LocalDate tradeDate; // 거래일
    @Column(name = "close_price", precision = 19, scale = 6)
    private BigDecimal closePrice; // 종가
    @Column(name = "accumulated_volume")
    private Long accumulatedVolume; // 전체 누적 거래량
    @Column(name = "short_sale_quantity")
    private Long shortSaleQuantity; // 공매도 거래량
    @Column(name = "short_sale_volume_ratio", precision = 12, scale = 6)
    private BigDecimal shortSaleVolumeRatio; // 전체 거래량 중 공매도 비율
    @Column(name = "accumulated_trade_amount", precision = 24, scale = 4)
    private BigDecimal accumulatedTradeAmount; // 전체 거래대금
    @Column(name = "short_sale_trade_amount", precision = 24, scale = 4)
    private BigDecimal shortSaleTradeAmount; // 공매도 거래대금
    @Column(name = "short_sale_trade_amount_ratio", precision = 12, scale = 6)
    private BigDecimal shortSaleTradeAmountRatio; // 전체 거래대금 중 공매도 거래대금 비율
    @Column(name = "average_price", precision = 19, scale = 6)
    private BigDecimal averagePrice; // 공매도 평균가격
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockShortSaleDaily create(Stock stock, LocalDate tradeDate,
            BigDecimal closePrice, Long accumulatedVolume, Long shortSaleQuantity,
            BigDecimal shortSaleVolumeRatio, BigDecimal accumulatedTradeAmount,
            BigDecimal shortSaleTradeAmount, BigDecimal shortSaleTradeAmountRatio,
            BigDecimal averagePrice) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(tradeDate, "거래일은 필수입니다.");
        DomesticStockShortSaleDaily daily = new DomesticStockShortSaleDaily();
        daily.stock = stock;
        daily.tradeDate = tradeDate;
        daily.apply(closePrice, accumulatedVolume, shortSaleQuantity, shortSaleVolumeRatio,
                accumulatedTradeAmount, shortSaleTradeAmount, shortSaleTradeAmountRatio, averagePrice);
        return daily;
    }

    public void update(BigDecimal closePrice, Long accumulatedVolume, Long shortSaleQuantity,
            BigDecimal shortSaleVolumeRatio, BigDecimal accumulatedTradeAmount,
            BigDecimal shortSaleTradeAmount, BigDecimal shortSaleTradeAmountRatio,
            BigDecimal averagePrice) {
        apply(closePrice, accumulatedVolume, shortSaleQuantity, shortSaleVolumeRatio,
                accumulatedTradeAmount, shortSaleTradeAmount, shortSaleTradeAmountRatio, averagePrice);
    }

    private void apply(BigDecimal closePrice, Long accumulatedVolume, Long shortSaleQuantity,
            BigDecimal shortSaleVolumeRatio, BigDecimal accumulatedTradeAmount,
            BigDecimal shortSaleTradeAmount, BigDecimal shortSaleTradeAmountRatio,
            BigDecimal averagePrice) {
        this.closePrice = closePrice;
        this.accumulatedVolume = accumulatedVolume;
        this.shortSaleQuantity = shortSaleQuantity;
        this.shortSaleVolumeRatio = shortSaleVolumeRatio;
        this.accumulatedTradeAmount = accumulatedTradeAmount;
        this.shortSaleTradeAmount = shortSaleTradeAmount;
        this.shortSaleTradeAmountRatio = shortSaleTradeAmountRatio;
        this.averagePrice = averagePrice;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist void prePersist() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
