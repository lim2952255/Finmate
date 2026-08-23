package com.finmate.domain.stock.price;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.detail.StockChartInterval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.finmate.global.validation.NumericValidator.validateNonNegative;
import static com.finmate.global.validation.NumericValidator.validateNullableNonNegative;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 주봉/월봉/연봉을 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_period_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_period_price_stock_interval_date_adjusted",
                columnNames = {"stock_id", "interval_type", "candle_date", "adjusted_price"}
        )
)
@Entity
public class StockPeriodPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false, length = 10, updatable = false)
    private StockChartInterval interval; // 주봉 / 월봉 / 연봉인지를 나타내는 ENUM

    @Column(name = "candle_date", nullable = false, updatable = false)
    private LocalDate candleDate; // 봉 기준일자

    @Column(name = "open_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal openPrice; // 시가

    @Column(name = "high_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal highPrice; // 고가

    @Column(name = "low_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal lowPrice; // 저가

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice; // 종가

    @Column(name = "accumulated_volume", nullable = false)
    private Long accumulatedVolume; // 거래량

    @Column(name = "accumulated_trade_amount", precision = 24, scale = 4)
    private BigDecimal accumulatedTradeAmount; // 거래대금

    @Column(name = "adjusted_price", nullable = false, updatable = false)
    private boolean adjustedPrice; // 수정주가 사용여부

    @Column(name = "last_fetched_at", nullable = false)
    private LocalDateTime lastFetchedAt; // KIS API로부터 마지막으로 데이터를 받은 시점

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막으로 업데이트된 시점

    public static StockPeriodPrice create(Stock stock,
                                          StockChartInterval interval,
                                          LocalDate candleDate,
                                          BigDecimal openPrice,
                                          BigDecimal highPrice,
                                          BigDecimal lowPrice,
                                          BigDecimal closePrice,
                                          Long accumulatedVolume,
                                          BigDecimal accumulatedTradeAmount,
                                          boolean adjustedPrice,
                                          LocalDateTime lastFetchedAt) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(interval, "봉 주기는 필수입니다.");
        if (interval == StockChartInterval.DAY) {
            throw new IllegalArgumentException("일봉은 stock_daily_price에 저장해야 합니다.");
        }
        validateRequired(candleDate, "봉 기준일은 필수입니다.");
        validatePrices(openPrice, highPrice, lowPrice, closePrice);
        validateNonNegative(accumulatedVolume, "누적 거래량은 0 이상이어야 합니다.");
        validateNullableNonNegative(accumulatedTradeAmount, "누적 거래대금은 0 이상이어야 합니다.");
        validateRequired(lastFetchedAt, "API 조회 시각은 필수입니다.");

        StockPeriodPrice price = new StockPeriodPrice();
        price.stock = stock;
        price.interval = interval;
        price.candleDate = candleDate;
        price.openPrice = openPrice;
        price.highPrice = highPrice;
        price.lowPrice = lowPrice;
        price.closePrice = closePrice;
        price.accumulatedVolume = accumulatedVolume;
        price.accumulatedTradeAmount = accumulatedTradeAmount;
        price.adjustedPrice = adjustedPrice;
        price.lastFetchedAt = lastFetchedAt;
        return price;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static void validatePrices(BigDecimal openPrice,
                                       BigDecimal highPrice,
                                       BigDecimal lowPrice,
                                       BigDecimal closePrice) {
        validateNonNegative(openPrice, "시가는 0 이상이어야 합니다.");
        validateNonNegative(highPrice, "고가는 0 이상이어야 합니다.");
        validateNonNegative(lowPrice, "저가는 0 이상이어야 합니다.");
        validateNonNegative(closePrice, "종가는 0 이상이어야 합니다.");
        if (highPrice.compareTo(lowPrice) < 0) {
            throw new IllegalArgumentException("고가는 저가보다 작을 수 없습니다.");
        }
    }
}
