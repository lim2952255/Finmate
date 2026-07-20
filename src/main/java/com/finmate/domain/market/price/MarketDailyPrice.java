package com.finmate.domain.market.price;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

// 주가 지수 / 환율 정보를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "market_daily_price",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_daily_price_indicator_date",
                        columnNames = {"indicator_type", "indicator_symbol", "trade_date"}
                )
        }
)
@Entity
public class MarketDailyPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 30, updatable = false)
    private MarketIndicatorType indicatorType; // 주가지수를 나타내는지 or 환율을 나타내는지를 가리키는 type정보

    @Column(name = "indicator_symbol", nullable = false, length = 30, updatable = false)
    private String indicatorSymbol; // 주가지수 or 환율의 종목코드

    @Column(name = "trade_date", nullable = false, updatable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "accumulated_volume")
    private Long accumulatedVolume;

    @Column(nullable = false)
    private boolean modified;

    @Column(name = "last_fetched_at", nullable = false)
    private LocalDateTime lastFetchedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static MarketDailyPrice create(MarketIndicatorSymbol indicator,
                                          LocalDate tradeDate,
                                          BigDecimal openPrice,
                                          BigDecimal highPrice,
                                          BigDecimal lowPrice,
                                          BigDecimal closePrice,
                                          Long accumulatedVolume,
                                          boolean modified,
                                          LocalDateTime lastFetchedAt) {
        validateRequired(indicator, "지수/환율 정보는 필수입니다.");
        validateRequired(tradeDate, "거래일은 필수입니다.");
        validatePrices(openPrice, highPrice, lowPrice, closePrice);
        validateNullableNonNegative(accumulatedVolume, "누적 거래량은 0 이상이어야 합니다.");
        validateRequired(lastFetchedAt, "API 조회 시각은 필수입니다.");

        MarketDailyPrice marketDailyPrice = new MarketDailyPrice();
        marketDailyPrice.indicatorType = indicator.getType();
        marketDailyPrice.indicatorSymbol = indicator.getKisSymbol();
        marketDailyPrice.tradeDate = tradeDate;
        marketDailyPrice.openPrice = openPrice;
        marketDailyPrice.highPrice = highPrice;
        marketDailyPrice.lowPrice = lowPrice;
        marketDailyPrice.closePrice = closePrice;
        marketDailyPrice.accumulatedVolume = accumulatedVolume;
        marketDailyPrice.modified = modified;
        marketDailyPrice.lastFetchedAt = lastFetchedAt;
        return marketDailyPrice;
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

    private static void validatePrices(BigDecimal openPrice,
                                       BigDecimal highPrice,
                                       BigDecimal lowPrice,
                                       BigDecimal closePrice) {
        validateNonNegative(openPrice, "시가는 0 이상이어야 합니다.");
        validateNonNegative(highPrice, "고가는 0 이상이어야 합니다.");
        validateNonNegative(lowPrice, "저가는 0 이상이어야 합니다.");
        validateNonNegative(closePrice, "종가는 0 이상이어야 합니다.");

        if (highPrice.compareTo(lowPrice) < 0) {
            throw new RuntimeException("고가는 저가보다 작을 수 없습니다.");
        }
    }

}
