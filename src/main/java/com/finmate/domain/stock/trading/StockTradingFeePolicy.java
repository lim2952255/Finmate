package com.finmate.domain.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.stock.StockMarketType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import static com.finmate.domain.stock.trading.TradingAmountValidator.normalizeCurrencyAmount;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 주식 시장별 거래 수수료와 매도 세금 정책
public enum StockTradingFeePolicy {
    KOSPI(StockMarketType.KOSPI, new BigDecimal("0.00015"), new BigDecimal("0.0020")),
    KOSDAQ(StockMarketType.KOSDAQ, new BigDecimal("0.00015"), new BigDecimal("0.0020")),
    NASDAQ(StockMarketType.NASDAQ, new BigDecimal("0.0025"), new BigDecimal("0.0000206"));

    private final StockMarketType marketType;
    private final BigDecimal commissionRate;
    private final BigDecimal sellTaxRate;

    StockTradingFeePolicy(StockMarketType marketType, BigDecimal commissionRate, BigDecimal sellTaxRate) {
        this.marketType = marketType; // 시장
        this.commissionRate = commissionRate; // 거래 수수료
        this.sellTaxRate = sellTaxRate; // 매도 세금
    }
    // 특정 시장의 거래 수수료 + 매도 세금 정보를 리턴하는 메서드
    public static StockTradingFeePolicy from(StockMarketType marketType) {
        validateRequired(marketType, "시장 구분은 필수입니다.");

        return Arrays.stream(values())
                .filter(policy -> policy.marketType == marketType)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("지원하지 않는 주식시장 수수료 정책입니다. marketType=" + marketType));
    }

    // 수수료 계산 -> 거래대금 * 수수료율
    public BigDecimal calculateCommissionAmount(CurrencyCode currencyCode, BigDecimal grossAmount) {
        return calculateRateAmount(currencyCode, grossAmount, commissionRate);
    }

    // 세금 계산 -> 거래 대금 * 매도세율
    public BigDecimal calculateTaxAmount(CurrencyCode currencyCode, StockOrderSide side, BigDecimal grossAmount) {
        validateRequired(side, "매수/매도 구분은 필수입니다.");

        // 매수의 경우에는 기본적으로 세금이 붙지않는다.
        if (side == StockOrderSide.BUY) {
            return zeroAmount(currencyCode);
        }

        // 매도의 경우에는 거래 대금 * 매도세율을 통해서 세금을 계산한다.
        return calculateRateAmount(currencyCode, grossAmount, sellTaxRate);
    }

    // 최종 정산금액을 계산한다.
    public BigDecimal calculateSettlementAmount(CurrencyCode currencyCode,
                                                StockOrderSide side,
                                                BigDecimal grossAmount) {
        BigDecimal commissionAmount = calculateCommissionAmount(currencyCode, grossAmount); // 거래 수수료
        BigDecimal taxAmount = calculateTaxAmount(currencyCode, side, grossAmount); // 세금

        if (side == StockOrderSide.BUY) {
            return grossAmount.add(commissionAmount); // 매수의 경우에는 거래대금 + 거래 수수료을 내야 한다
        }

        return grossAmount.subtract(commissionAmount).subtract(taxAmount); // 매도의 경우에는 거래대금 - 거래 수수료 - 세금만큼 받는다.
    }

    public StockMarketType getMarketType() {
        return marketType;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getSellTaxRate() {
        return sellTaxRate;
    }

    // 수수료율 / 세율을 기반으로 실제 거래 수수료 및 세금을 계산한다.
    private BigDecimal calculateRateAmount(CurrencyCode currencyCode, BigDecimal grossAmount, BigDecimal rate) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        validateRequired(grossAmount, "거래금액은 필수입니다.");
        validateRequired(rate, "요율은 필수입니다.");

        return normalizeCurrencyAmount(currencyCode, grossAmount.multiply(rate), RoundingMode.HALF_UP);
    }

    private BigDecimal zeroAmount(CurrencyCode currencyCode) {
        validateRequired(currencyCode, "통화는 필수입니다.");

        return BigDecimal.ZERO.setScale(currencyCode.getFractionDigits(), RoundingMode.UNNECESSARY);
    }
}
