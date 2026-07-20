package com.finmate.domain.stock.price;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.finmate.global.validation.NumericValidator.validateNonNegative;
import static com.finmate.global.validation.NumericValidator.validateNullableNonNegative;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_daily_price",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_daily_price_stock_date_adjusted",
                        columnNames = {"stock_id", "trade_date", "adjusted_price"}
                )
        }
)
@Entity
public class StockDailyPrice {
    // 일봉 데이터의 내부 PK다. 종목 식별에는 사용하지 않고 연관관계 연결용 대리키로 사용한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 일봉 데이터가 속한 종목이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    // 거래일이다. 국내 API의 stck_bsop_date, 해외 API의 xymd에 해당한다.
    @Column(name = "trade_date", nullable = false, updatable = false)
    private LocalDate tradeDate;

    // 해당 거래일의 시가다(거래일에 처음 체결된 가격). 국내 API의 stck_oprc, 해외 API의 open에 해당한다.
    @Column(name = "open_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal openPrice;

    // 해당 거래일의 고가다(거래일동안 가장 높게 체결된 가격). 국내 API의 stck_hgpr, 해외 API의 high에 해당한다.
    @Column(name = "high_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal highPrice;

    // 해당 거래일의 저가다(거래일동안 가장 낮게 체결된 가격). 국내 API의 stck_lwpr, 해외 API의 low에 해당한다.
    @Column(name = "low_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal lowPrice;

    // 해당 거래일의 종가다(거래일에 마지막으로 체결된 가격). 국내 API의 stck_clpr, 해외 API의 clos에 해당한다.
    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;

    // 해당 거래일의 누적 거래량이다. 국내 API의 acml_vol, 해외 API의 tvol에 해당한다.
    @Column(name = "accumulated_volume", nullable = false)
    private Long accumulatedVolume;

    // 해당 거래일의 누적 거래대금이다. 국내 API의 acml_tr_pbmn, 해외 dailyprice API의 tamt에 해당한다.
    // 일부 API나 시장에서는 거래대금을 주지 않을 수 있으므로 null을 허용한다.
    @Column(name = "accumulated_trade_amount", precision = 24, scale = 4)
    private BigDecimal accumulatedTradeAmount;

    // 수정주가 기준 데이터인지 여부다. 국내는 FID_ORG_ADJ_PRC, 해외는 MODP 호출 정책에 맞춰 저장한다.
    // 수정주는 액면분할, 병합, 배당락같은 이벤트를 반영해서 과거 가격을 조정한 값
    // 액면분할이란 하나의 주를 여러 주로 나누는 경우, 주가가 하락하는 것처럼 보이기 때문에, 과거 가격을 같은 기준으로 비교할 수 있도록 조정한 것이 수정주가이다.
    @Column(name = "adjusted_price", nullable = false, updatable = false)
    private boolean adjustedPrice;

    // API가 과거 가격 변경 데이터라고 표시했는지 여부다. 국내 API의 mod_yn에 해당한다.
    @Column(nullable = false)
    private boolean modified;

    // KIS API를 통해 이 일봉 row를 마지막으로 받아온 시각이다.
    @Column(name = "last_fetched_at", nullable = false)
    private LocalDateTime lastFetchedAt;

    // 이 일봉 row가 처음 저장된 시각이다.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 이 일봉 row가 마지막으로 수정된 시각이다.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockDailyPrice create(Stock stock,
                                         LocalDate tradeDate,
                                         BigDecimal openPrice,
                                         BigDecimal highPrice,
                                         BigDecimal lowPrice,
                                         BigDecimal closePrice,
                                         Long accumulatedVolume,
                                         BigDecimal accumulatedTradeAmount,
                                         boolean adjustedPrice,
                                         boolean modified,
                                         LocalDateTime lastFetchedAt) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(tradeDate, "거래일은 필수입니다.");
        validatePrices(openPrice, highPrice, lowPrice, closePrice);
        validateNonNegative(accumulatedVolume, "누적 거래량은 0 이상이어야 합니다.");
        validateNullableNonNegative(accumulatedTradeAmount, "누적 거래대금은 0 이상이어야 합니다.");
        validateRequired(lastFetchedAt, "API 조회 시각은 필수입니다.");

        StockDailyPrice stockDailyPrice = new StockDailyPrice();
        stockDailyPrice.stock = stock;
        stockDailyPrice.tradeDate = tradeDate;
        stockDailyPrice.openPrice = openPrice;
        stockDailyPrice.highPrice = highPrice;
        stockDailyPrice.lowPrice = lowPrice;
        stockDailyPrice.closePrice = closePrice;
        stockDailyPrice.accumulatedVolume = accumulatedVolume;
        stockDailyPrice.accumulatedTradeAmount = accumulatedTradeAmount;
        stockDailyPrice.adjustedPrice = adjustedPrice;
        stockDailyPrice.modified = modified;
        stockDailyPrice.lastFetchedAt = lastFetchedAt;
        return stockDailyPrice;
    }

    // 확정된 일봉은 일반적으로 바꾸지 않는다.
    // 단, 액면분할/수정주가 재산정/API 정정으로 같은 거래일 데이터가 바뀐 경우에만 보정한다.
    public void updateFromApiCorrection(BigDecimal openPrice,
                                        BigDecimal highPrice,
                                        BigDecimal lowPrice,
                                        BigDecimal closePrice,
                                        Long accumulatedVolume,
                                        BigDecimal accumulatedTradeAmount,
                                        boolean modified,
                                        LocalDateTime lastFetchedAt) {
        validatePrices(openPrice, highPrice, lowPrice, closePrice);
        validateNonNegative(accumulatedVolume, "누적 거래량은 0 이상이어야 합니다.");
        validateNullableNonNegative(accumulatedTradeAmount, "누적 거래대금은 0 이상이어야 합니다.");
        validateRequired(lastFetchedAt, "API 조회 시각은 필수입니다.");

        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.accumulatedVolume = accumulatedVolume;
        this.accumulatedTradeAmount = accumulatedTradeAmount;
        this.modified = modified;
        this.lastFetchedAt = lastFetchedAt;
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
