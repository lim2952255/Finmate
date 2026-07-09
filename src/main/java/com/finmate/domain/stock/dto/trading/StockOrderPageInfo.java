package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.domain.stock.trading.StockOrderType;
import lombok.Getter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 사용자가 종목 주문 페이지에 들어갔을때, 화면에 필요한 데이터를 한번에 묶어서 전달하기 위한 dto
@Getter
public class StockOrderPageInfo {
    private final Stock stock;
    private final CurrencyCode currencyCode;
    private final List<Investment> investments;
    private final Long defaultInvestmentId;
    private final StockHolding holding;
    private final List<StockOrderAccountSummary> accountSummaries;
    private final Map<Long, StockOrderAccountSummary> accountSummaryMap;
    private final BigDecimal buyExecutablePrice;
    private final BigDecimal sellExecutablePrice;
    private final BigDecimal tradePrice;
    private final StockOrderSide[] sides = StockOrderSide.values();
    private final StockOrderType[] orderTypes = StockOrderType.values();
    private final StockOrderTriggerCondition[] triggerConditions = StockOrderTriggerCondition.values();

    public StockOrderPageInfo(Stock stock,
                              CurrencyCode currencyCode,
                              List<Investment> investments,
                              Long defaultInvestmentId,
                              StockHolding holding,
                              List<StockHolding> holdings,
                              BigDecimal buyExecutablePrice,
                              BigDecimal sellExecutablePrice,
                              BigDecimal tradePrice) {
        this.stock = stock;
        this.currencyCode = currencyCode;
        this.investments = investments;
        this.defaultInvestmentId = defaultInvestmentId;
        this.holding = holding;
        Map<Long, StockHolding> holdingsByInvestmentId = holdings.stream()
                .collect(Collectors.toMap(
                        stockHolding -> stockHolding.getInvestment().getId(),
                        Function.identity()
                ));
        this.accountSummaries = investments.stream()
                .map(investment -> StockOrderAccountSummary.from(
                        investment,
                        currencyCode,
                        holdingsByInvestmentId.get(investment.getId())))
                .toList();
        this.accountSummaryMap = accountSummaries.stream()
                .collect(Collectors.toMap(
                        StockOrderAccountSummary::getInvestmentId,
                        Function.identity()
                ));
        this.buyExecutablePrice = buyExecutablePrice;
        this.sellExecutablePrice = sellExecutablePrice;
        this.tradePrice = tradePrice;
    }

    public StockOrderAccountSummary getAccountSummary(Long investmentId) {
        return accountSummaryMap.get(investmentId);
    }

    public String formatPrice(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(currencyCode.getFractionDigits());
        return formatter.format(value);
    }

    public String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(6);
        return formatter.format(value);
    }
}
