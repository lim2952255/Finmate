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

// 국내 주식 종목별 일일 투자자 매매동향을 저장한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_investor_daily_trade",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_investor_daily_trade_key",
                columnNames = {"stock_id", "market_code", "trade_date"}
        )
)
@Entity
public class DomesticStockInvestorDailyTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Column(name = "market_code", nullable = false, length = 4, updatable = false)
    private String marketCode; // KRX 또는 NXT

    @Column(name = "trade_date", nullable = false, updatable = false)
    private LocalDate tradeDate; // 기준 거래일

    @Column(name = "close_price", precision = 19, scale = 6)
    private BigDecimal closePrice; // 종가

    @Column
    private Long volume; // 해당 기준일에 거래된 총 주식수(거래량)

    @Column(name = "trade_amount", precision = 24, scale = 4)
    private BigDecimal tradeAmount; // 해당 기준일에 거래된 총 금액(거래대금)

    @Column(name = "foreign_buy_quantity")
    private Long foreignBuyQuantity; // 외국인이 매수한 주식 수

    @Column(name = "foreign_sell_quantity")
    private Long foreignSellQuantity; // 외국인이 매도한 주식 수

    @Column(name = "foreign_net_quantity")
    private Long foreignNetQuantity; // 외국인의 순매수 수량: 매수량 - 매도량

    @Column(name = "foreign_buy_amount", precision = 24, scale = 4)
    private BigDecimal foreignBuyAmount; // 외국인이 매수한 거래금액

    @Column(name = "foreign_sell_amount", precision = 24, scale = 4)
    private BigDecimal foreignSellAmount; // 외국인이 매도한 거래금액

    @Column(name = "foreign_net_amount", precision = 24, scale = 4)
    private BigDecimal foreignNetAmount; // 외국인의 순매수금액

    @Column(name = "personal_buy_quantity")
    private Long personalBuyQuantity; // 개인이 매수한 주식 수

    @Column(name = "personal_sell_quantity")
    private Long personalSellQuantity; // 개인이 매도한 주식 수

    @Column(name = "personal_net_quantity")
    private Long personalNetQuantity; // 개인의 순매수 수량: 매수량 - 매도량

    @Column(name = "personal_buy_amount", precision = 24, scale = 4)
    private BigDecimal personalBuyAmount; // 개인이 매수한 거래금액

    @Column(name = "personal_sell_amount", precision = 24, scale = 4)
    private BigDecimal personalSellAmount; // 개인이 매도한 거래금액

    @Column(name = "personal_net_amount", precision = 24, scale = 4)
    private BigDecimal personalNetAmount; // 개인의 순매수금액

    @Column(name = "institution_buy_quantity")
    private Long institutionBuyQuantity; // 기관이 매수한 주식 수

    @Column(name = "institution_sell_quantity")
    private Long institutionSellQuantity; // 기관이 매도한 주식 수

    @Column(name = "institution_net_quantity")
    private Long institutionNetQuantity; // 기관의 순매수 수량: 매수량 - 매도량

    @Column(name = "institution_buy_amount", precision = 24, scale = 4)
    private BigDecimal institutionBuyAmount; // 기관이 매수한 거래금액

    @Column(name = "institution_sell_amount", precision = 24, scale = 4)
    private BigDecimal institutionSellAmount; // 기관이 매도한 거래금액

    @Column(name = "institution_net_amount", precision = 24, scale = 4)
    private BigDecimal institutionNetAmount; // 기관의 순매수 금액

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockInvestorDailyTrade create(Stock stock,
                                                         String marketCode,
                                                         LocalDate tradeDate,
                                                         BigDecimal closePrice,
                                                         Long volume,
                                                         BigDecimal tradeAmount,
                                                         Long foreignBuyQuantity,
                                                         Long foreignSellQuantity,
                                                         Long foreignNetQuantity,
                                                         BigDecimal foreignBuyAmount,
                                                         BigDecimal foreignSellAmount,
                                                         BigDecimal foreignNetAmount,
                                                         Long personalBuyQuantity,
                                                         Long personalSellQuantity,
                                                         Long personalNetQuantity,
                                                         BigDecimal personalBuyAmount,
                                                         BigDecimal personalSellAmount,
                                                         BigDecimal personalNetAmount,
                                                         Long institutionBuyQuantity,
                                                         Long institutionSellQuantity,
                                                         Long institutionNetQuantity,
                                                         BigDecimal institutionBuyAmount,
                                                         BigDecimal institutionSellAmount,
                                                         BigDecimal institutionNetAmount) {
        validateKey(stock, marketCode, tradeDate);
        DomesticStockInvestorDailyTrade trade = new DomesticStockInvestorDailyTrade();
        trade.stock = stock;
        trade.marketCode = marketCode;
        trade.tradeDate = tradeDate;
        trade.apply(closePrice, volume, tradeAmount,
                foreignBuyQuantity, foreignSellQuantity, foreignNetQuantity,
                foreignBuyAmount, foreignSellAmount, foreignNetAmount,
                personalBuyQuantity, personalSellQuantity, personalNetQuantity,
                personalBuyAmount, personalSellAmount, personalNetAmount,
                institutionBuyQuantity, institutionSellQuantity, institutionNetQuantity,
                institutionBuyAmount, institutionSellAmount, institutionNetAmount);
        return trade;
    }

    public void update(BigDecimal closePrice,
                       Long volume,
                       BigDecimal tradeAmount,
                       Long foreignBuyQuantity,
                       Long foreignSellQuantity,
                       Long foreignNetQuantity,
                       BigDecimal foreignBuyAmount,
                       BigDecimal foreignSellAmount,
                       BigDecimal foreignNetAmount,
                       Long personalBuyQuantity,
                       Long personalSellQuantity,
                       Long personalNetQuantity,
                       BigDecimal personalBuyAmount,
                       BigDecimal personalSellAmount,
                       BigDecimal personalNetAmount,
                       Long institutionBuyQuantity,
                       Long institutionSellQuantity,
                       Long institutionNetQuantity,
                       BigDecimal institutionBuyAmount,
                       BigDecimal institutionSellAmount,
                       BigDecimal institutionNetAmount) {
        apply(closePrice, volume, tradeAmount,
                foreignBuyQuantity, foreignSellQuantity, foreignNetQuantity,
                foreignBuyAmount, foreignSellAmount, foreignNetAmount,
                personalBuyQuantity, personalSellQuantity, personalNetQuantity,
                personalBuyAmount, personalSellAmount, personalNetAmount,
                institutionBuyQuantity, institutionSellQuantity, institutionNetQuantity,
                institutionBuyAmount, institutionSellAmount, institutionNetAmount);
        this.updatedAt = LocalDateTime.now();
    }

    private void apply(BigDecimal closePrice,
                       Long volume,
                       BigDecimal tradeAmount,
                       Long foreignBuyQuantity,
                       Long foreignSellQuantity,
                       Long foreignNetQuantity,
                       BigDecimal foreignBuyAmount,
                       BigDecimal foreignSellAmount,
                       BigDecimal foreignNetAmount,
                       Long personalBuyQuantity,
                       Long personalSellQuantity,
                       Long personalNetQuantity,
                       BigDecimal personalBuyAmount,
                       BigDecimal personalSellAmount,
                       BigDecimal personalNetAmount,
                       Long institutionBuyQuantity,
                       Long institutionSellQuantity,
                       Long institutionNetQuantity,
                       BigDecimal institutionBuyAmount,
                       BigDecimal institutionSellAmount,
                       BigDecimal institutionNetAmount) {
        this.closePrice = closePrice;
        this.volume = volume;
        this.tradeAmount = tradeAmount;
        this.foreignBuyQuantity = foreignBuyQuantity;
        this.foreignSellQuantity = foreignSellQuantity;
        this.foreignNetQuantity = foreignNetQuantity;
        this.foreignBuyAmount = foreignBuyAmount;
        this.foreignSellAmount = foreignSellAmount;
        this.foreignNetAmount = foreignNetAmount;
        this.personalBuyQuantity = personalBuyQuantity;
        this.personalSellQuantity = personalSellQuantity;
        this.personalNetQuantity = personalNetQuantity;
        this.personalBuyAmount = personalBuyAmount;
        this.personalSellAmount = personalSellAmount;
        this.personalNetAmount = personalNetAmount;
        this.institutionBuyQuantity = institutionBuyQuantity;
        this.institutionSellQuantity = institutionSellQuantity;
        this.institutionNetQuantity = institutionNetQuantity;
        this.institutionBuyAmount = institutionBuyAmount;
        this.institutionSellAmount = institutionSellAmount;
        this.institutionNetAmount = institutionNetAmount;
    }

    private static void validateKey(Stock stock, String marketCode, LocalDate tradeDate) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(marketCode, "시장 코드는 필수입니다.");
        validateRequired(tradeDate, "거래일은 필수입니다.");
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
