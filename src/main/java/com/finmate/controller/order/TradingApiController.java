package com.finmate.controller.order;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.dto.trading.*;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockTradeTransaction;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.stock.trading.StockTradingCommandService;
import com.finmate.service.stock.trading.StockTradingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/trading")
public class TradingApiController {
    private final StockTradingQueryService queryService;
    private final StockTradingCommandService commandService;

    @GetMapping("/history")
    public HistoryResponse history(@RequestParam(required = false) Long investmentId,
                                   @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        StockTradingHistoryPageInfo info = queryService.getTradingHistoryPageInfo(principal.getId(), investmentId);
        return new HistoryResponse(info.getInvestments().stream().map(AccountInfo::from).toList(), info.getSelectedInvestment() == null ? null : info.getSelectedInvestment().getId(), info.isAllAccounts(),
                info.getOrders().stream().map(order -> OrderInfo.from(order, info.getAverageExecutionPrice(order))).toList(),
                info.getReservations().stream().map(ReservationInfo::from).toList(),
                info.getTransactions().stream().map(TradeInfo::from).toList());
    }

    @GetMapping("/order-page/{stockId}")
    public OrderPageResponse orderPage(@PathVariable Long stockId, @RequestParam(required = false) Long investmentId,
                                       @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        StockOrderPageInfo info = queryService.getOrderPageInfo(principal.getId(), stockId, investmentId);
        return new OrderPageResponse(info.getStock().getId(), info.getStock().getNameKo(), info.getStock().getSymbol(), info.getCurrencyCode().name(), info.getCurrencyCode().getFractionDigits(), info.getCurrencyCode().getInputStep().toPlainString(), info.getDefaultInvestmentId(),
                info.getInvestments().stream().map(AccountInfo::from).toList(), info.getAccountSummaries(), info.getTradePrice() == null ? null : info.getTradePrice().toPlainString(), info.getBuyExecutablePrice() == null ? null : info.getBuyExecutablePrice().toPlainString(), info.getSellExecutablePrice() == null ? null : info.getSellExecutablePrice().toPlainString(),
                List.of(info.getSides()), List.of(info.getOrderTypes()), List.of(info.getTriggerConditions()), info.isStockTradingAvailable(), info.getStockTradingTimeDescription());
    }

    @PostMapping("/orders") public ResponseEntity<Void> order(@RequestBody StockOrderRequest request, @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) { commandService.submitOrder(principal.getId(), request); return ResponseEntity.noContent().build(); }
    @PostMapping("/reservations") public ResponseEntity<Void> reservation(@RequestBody StockOrderReservationRequest request, @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) { commandService.submitReservation(principal.getId(), request); return ResponseEntity.noContent().build(); }
    @PostMapping("/orders/{id}/cancel") public ResponseEntity<Void> cancelOrder(@PathVariable Long id, @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) { commandService.cancelOrder(principal.getId(), id); return ResponseEntity.noContent().build(); }
    @PostMapping("/reservations/{id}/cancel") public ResponseEntity<Void> cancelReservation(@PathVariable Long id, @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) { commandService.cancelReservation(principal.getId(), id); return ResponseEntity.noContent().build(); }

    public record AccountInfo(Long id, String label, String companyName, String accountNumber) {
        static AccountInfo from(Investment value) {
            return new AccountInfo(value.getId(), value.getSecuritiesCompanyCode().getDisplayName() + " " + value.getAccountNumber(),
                    value.getSecuritiesCompanyCode().getDisplayName(), value.getAccountNumber());
        }
    }
    public record HistoryResponse(List<AccountInfo> accounts, Long selectedInvestmentId, boolean allAccounts, List<OrderInfo> orders, List<ReservationInfo> reservations, List<TradeInfo> trades) {}
    public record OrderInfo(Long id, Long stockId, String stockName, String symbol, String side, String orderType,
                            String status, String quantity, String executedQuantity, String price,
                            String averageExecutionPrice, String createdAt, String expiresAt, Long investmentId) {
        static OrderInfo from(StockOrder value, BigDecimal averageExecutionPrice) {
            return new OrderInfo(value.getId(), value.getStock().getId(), value.getStock().getNameKo(), value.getStock().getSymbol(),
                    value.getSide().name(), value.getOrderType().name(), value.getStatus().name(), value.getQuantity().toPlainString(),
                    value.getExecutedQuantity().toPlainString(), value.getOrderPrice() == null ? null : value.getOrderPrice().toPlainString(),
                    averageExecutionPrice == null ? null : averageExecutionPrice.toPlainString(), value.getCreatedAt().toString(),
                    value.getExpiresAt() == null ? null : value.getExpiresAt().toString(), value.getInvestment().getId());
        }
    }
    public record ReservationInfo(Long id, Long stockId, String stockName, String symbol, String side, String orderType,
                                  String status, String condition, String quantity, String triggerPrice, String orderPrice,
                                  String expiresAt, Long investmentId) {
        static ReservationInfo from(StockOrderReservation value) {
            return new ReservationInfo(value.getId(), value.getStock().getId(), value.getStock().getNameKo(), value.getStock().getSymbol(),
                    value.getSide().name(), value.getOrderType().name(), value.getStatus().name(), value.getTriggerCondition().name(),
                    value.getQuantity().toPlainString(), value.getTriggerPrice().toPlainString(),
                    value.getOrderPrice() == null ? null : value.getOrderPrice().toPlainString(),
                    value.getExpiresAt() == null ? null : value.getExpiresAt().toString(), value.getInvestment().getId());
        }
    }
    public record TradeInfo(Long id, Long stockId, String stockName, String symbol, String side, String quantity,
                            String price, String netAmount, String commission, String tax, String currency,
                            String executedAt, Long investmentId) {
        static TradeInfo from(StockTradeTransaction value) {
            return new TradeInfo(value.getId(), value.getStock().getId(), value.getStock().getNameKo(), value.getStock().getSymbol(),
                    value.getSide().name(), value.getQuantity().toPlainString(), value.getExecutionPrice().toPlainString(),
                    value.getNetCashAmount().toPlainString(), value.getCommissionAmount().toPlainString(), value.getTaxAmount().toPlainString(),
                    value.getCurrencyCode().name(), value.getExecutedAt().toString(), value.getInvestment().getId());
        }
    }
    public record OrderPageResponse(Long stockId, String stockName, String symbol, String currency, int fractionDigits, String inputStep, Long defaultInvestmentId, List<AccountInfo> accounts, List<StockOrderAccountSummary> summaries, String tradePrice, String buyExecutablePrice, String sellExecutablePrice, List<StockOrderSide> sides, List<StockOrderType> orderTypes, List<StockOrderTriggerCondition> triggerConditions, boolean tradingAvailable, String tradingTimeDescription) {}
}
