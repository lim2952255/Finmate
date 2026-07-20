package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 주식거래내역 페이지에 전달할 데이터를 모아놓은 dto
@Getter
public class StockTradingHistoryPageInfo {
    private final List<Investment> investments;
    private final Investment selectedInvestment;
    private final List<StockOrder> orders;
    private final List<StockOrderReservation> reservations; // 예약 내역
    private final List<StockTradeTransaction> transactions; // 거래내역
    private final boolean allAccounts;

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
    }

    public String formatDecimal(BigDecimal value, int fractionDigits) {
        return DisplayFormatUtils.formatDecimal(value, fractionDigits);
    }
}
