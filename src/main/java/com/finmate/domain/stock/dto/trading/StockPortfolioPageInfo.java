package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.trading.StockHolding;
import lombok.Getter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class StockPortfolioPageInfo {
    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final List<StockHolding> holdings;
    private final boolean allAccounts;
    private final List<CurrencyCode> currencies;
    private final Map<CurrencyCode, BigDecimal> totalPurchaseAmountsByCurrency;

    public StockPortfolioPageInfo(List<Investment> investments,
                                  Investment selectedInvestment,
                                  List<StockHolding> holdings,
                                  boolean allAccounts) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.holdings = holdings;
        this.allAccounts = allAccounts;
        this.totalPurchaseAmountsByCurrency = holdings.stream()
                .collect(Collectors.groupingBy(
                        StockHolding::getCurrencyCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                holding -> holding.getAveragePurchasePrice().multiply(holding.getQuantity()),
                                BigDecimal::add
                        )
                ));
        this.currencies = Arrays.stream(CurrencyCode.values())
                .filter(totalPurchaseAmountsByCurrency::containsKey)
                .toList();
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

    public String formatDecimal(BigDecimal value, int fractionDigits) {
        if (value == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(fractionDigits);
        return formatter.format(value);
    }

    public BigDecimal getTotalPurchaseAmount(CurrencyCode currencyCode) {
        return totalPurchaseAmountsByCurrency.getOrDefault(currencyCode, BigDecimal.ZERO);
    }
}
