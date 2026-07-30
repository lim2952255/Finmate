package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 주식거래내역 페이지에 전달할 데이터를 모아놓은 dto
@Getter
public class StockTradingHistoryPageInfo {
    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final List<StockOrder> orders;
    private final List<StockOrderReservation> reservations; // 예약 내역
    private final List<StockTradeTransaction> transactions; // 거래내역
    private final boolean allAccounts;
    private final Map<Long, BigDecimal> averageExecutionPricesByOrderId;

    public StockTradingHistoryPageInfo(List<Investment> investments,
                                       Investment selectedInvestment,
                                       List<StockOrder> orders,
                                       List<StockOrderReservation> reservations,
                                       List<StockTradeTransaction> transactions,
                                       boolean allAccounts) {
        this.investments = investments;
        this.selectedInvestment = selectedInvestment;
        this.orders = orders;
        this.reservations = reservations;
        this.transactions = transactions;
        this.allAccounts = allAccounts;
        this.averageExecutionPricesByOrderId = calculateAverageExecutionPrices(transactions);
    }

    public String formatDecimal(BigDecimal value, int fractionDigits) {
        return DisplayFormatUtils.formatDecimal(value, fractionDigits);
    }

    public BigDecimal getAverageExecutionPrice(StockOrder order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        return averageExecutionPricesByOrderId.get(order.getId());
    }

    private Map<Long, BigDecimal> calculateAverageExecutionPrices(List<StockTradeTransaction> transactions) {
        Map<Long, BigDecimal> totalQuantitiesByOrderId = new HashMap<>();
        Map<Long, BigDecimal> totalAmountsByOrderId = new HashMap<>();
        for (StockTradeTransaction transaction : transactions) {
            if (transaction.getOrder() == null || transaction.getOrder().getId() == null) {
                continue;
            }

            Long orderId = transaction.getOrder().getId();
            totalQuantitiesByOrderId.merge(orderId, transaction.getQuantity(), BigDecimal::add);
            totalAmountsByOrderId.merge(
                    orderId,
                    transaction.getExecutionPrice().multiply(transaction.getQuantity()),
                    BigDecimal::add);
        }

        Map<Long, BigDecimal> averagePrices = new HashMap<>();
        totalAmountsByOrderId.forEach((orderId, totalAmount) -> {
            BigDecimal totalQuantity = totalQuantitiesByOrderId.get(orderId);
            if (totalQuantity != null && totalQuantity.signum() > 0) {
                averagePrices.put(orderId, totalAmount.divide(totalQuantity, 10, RoundingMode.HALF_UP));
            }
        });
        return Map.copyOf(averagePrices);
    }
}
