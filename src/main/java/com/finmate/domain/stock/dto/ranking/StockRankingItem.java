package com.finmate.domain.stock.dto.ranking;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// 랭킹 목록에서 한 줄(row)에 표시될 종목 정보를 담는 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockRankingItem {
    private int rank;
    private Long stockId;
    private StockMarketType marketType;
    private StockRankingType rankingType;
    private String symbol;
    private String nameKo;
    private String nameEn;
    private BigDecimal currentPrice;
    private BigDecimal changeAmount;
    private BigDecimal changeRate;
    private Long accumulatedVolume;
    private BigDecimal accumulatedTradeAmount;
    private String currency;

    public boolean hasStockId() {
        return stockId != null;
    }

    public String getDisplayName() {
        if (nameKo != null && !nameKo.isBlank()) {
            return nameKo;
        }

        if (nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }

        return symbol;
    }

    public String getDisplayCurrentPrice() {
        if (currentPrice == null) {
            return "-";
        }

        return currencyPrefix() + DisplayFormatUtils.formatFixedDecimal(currentPrice, isKrw() ? 0 : 2);
    }

    public String getDisplayChangeRate() {
        if (changeRate == null) {
            return "-";
        }

        return DisplayFormatUtils.formatSignedPercent(changeRate, 2);
    }

    public String getChangeRateClass() {
        if (changeRate == null || changeRate.signum() == 0) {
            return "flat";
        }

        return changeRate.signum() > 0 ? "up" : "down";
    }

    public String getDisplayAccumulatedVolume() {
        if (accumulatedVolume == null) {
            return "-";
        }

        return DisplayFormatUtils.formatInteger(accumulatedVolume);
    }

    public String getDisplayAccumulatedTradeAmount() {
        if (accumulatedTradeAmount == null) {
            return "-";
        }

        String suffix = isKrw() ? "원" : "";
        return currencyPrefix() + DisplayFormatUtils.formatFixedDecimal(accumulatedTradeAmount, isKrw() ? 0 : 2) + suffix;
    }

    private boolean isKrw() {
        return "KRW".equalsIgnoreCase(currency);
    }

    private String currencyPrefix() {
        if ("USD".equalsIgnoreCase(currency)) {
            return "$";
        }

        return "";
    }

}
