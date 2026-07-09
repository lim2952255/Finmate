package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrder;
import com.finmate.domain.stock.trading.StockOrderReservation;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.stock.trading.StockTradingFeePolicy;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.stock.trading.StockHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.finmate.domain.stock.trading.TradingAmountValidator.calculateAmount;

// 주식 주문을 접수하기 전에, 예수금이나 보유 주식을 예약 및 잠금 처리하고, 주문이 취소 및 만료되면 예약 및 잠금을 다시 푸는 서비스
@Service
@RequiredArgsConstructor
public class StockTradingAssetService {
    private final InvestmentCashBalanceRepository investmentCashBalanceRepository; // 각계좌의 통화별 잔고를 저장하는 레파지터리
    private final StockHoldingRepository stockHoldingRepository; // 각 계좌별 보유중인 종목의 수량을 저장하는 레파지터리
    private final StockTradingRealtimePriceService realtimePriceService; // 실시간 체결 기준 가격
    private final StockTradingLookupService lookupService;

    // 주문을 접수하기 전에 주문에 필요한 자산 및 종목 수량을 잠그는 메서드
    ReservedAsset reserveAsset(Investment investment,
                               Stock stock,
                               StockOrderSide side,
                               StockOrderType orderType,
                               BigDecimal quantity,
                               BigDecimal orderPrice,
                               BigDecimal triggerPrice) {
        CurrencyCode currencyCode = lookupService.currencyCode(stock);
        if (side == StockOrderSide.SELL) { // 주식 매도의 경우, 매도할 수량만큼의 종목을 잠가야 한다.
            // 현재 계좌가 보유중인 종목의 수량을 조회한다.
            StockHolding holding = stockHoldingRepository.findByInvestmentIdAndStockIdForUpdate(investment.getId(), stock.getId())
                    .orElseThrow(() -> new RuntimeException("보유 종목이 없습니다."));
            holding.lockQuantity(quantity); // 해당 수량 만큼의 종목을 잠근다.
            return new ReservedAsset(BigDecimal.ZERO.setScale(currencyCode.getFractionDigits()), quantity); // lock 정보를 리턴한다.
        }

        // 주식 매수의 경우, 매수할 금액만큼의 자산을 잠가야 한다.
        BigDecimal reservePrice = orderType == StockOrderType.LIMIT
                ? orderPrice // 지정가 주문이면 지정가를 기준으로 예수금을 잠근다.
                : triggerPrice != null ? triggerPrice : realtimePriceService.getExecutablePrice(stock, StockOrderSide.BUY); // 시장가 또는 예약 주문 경우, 예약 주문 가격이 존재한다면 해당 가격을 기반으로 예수금을 잠그고, 존재하지 않으면 RealtimeService를 통해 실시간 체결 기준 가격을 받는다.
        BigDecimal grossAmount = calculateAmount(currencyCode, reservePrice, quantity, RoundingMode.CEILING); // 총 거래대금을 계산
        BigDecimal reserveAmount = StockTradingFeePolicy.from(stock.getMarketType()) // 지식 매수의 경우, 총 거래대금 + 거래 수수료 만큼의 예수금을 잠가야 한다.
                .calculateSettlementAmount(currencyCode, StockOrderSide.BUY, grossAmount);
        findCashBalanceForUpdate(investment.getId(), currencyCode).lock(reserveAmount); // InvestmentCashBalance에서 reserveAmount만큼의 금액을 잠근다.
        return new ReservedAsset(reserveAmount, BigDecimal.ZERO); // lock 정보를 리턴한다.
    }

    // 일반주문 StockOrder에 잠겨있던 예수금이나 주식 수량을 해제하는 메서드
    void releaseOrderAsset(StockOrder order) {
        // 만약 매수 주문 + 잠겨있는 예수금이 0보다 크면, 해당 증권 계좌의 CashBalance에서 잠겨있는 예수금을 해제한다.
        if (order.getSide() == StockOrderSide.BUY && order.getReservedCashAmount().compareTo(BigDecimal.ZERO) > 0) {
            findCashBalanceForUpdate(order.getInvestment().getId(), order.getCurrencyCode())
                    .releaseLocked(order.getReservedCashAmount());
            return;
        }
        // 만약 매도 주문 + 잠겨있는 주식 수량이 0보다 크면, 해당 증권계좌가 보유중인 종목 수(StockHolding)에서 잠겨있는 주식 수량을 해제한다.
        if (order.getSide() == StockOrderSide.SELL && order.getReservedStockQuantity().compareTo(BigDecimal.ZERO) > 0) {
            stockHoldingRepository.findByInvestmentIdAndStockIdForUpdate(order.getInvestment().getId(), order.getStock().getId())
                    .orElseThrow(() -> new RuntimeException("보유 종목이 없습니다."))
                    .releaseLockedQuantity(order.getReservedStockQuantity());
        }
    }
    // 예약 주문 StockOrderReservation에 잠겨있던 예수금이나 주식 수량을 해제하는 메서드
    void releaseReservationAsset(StockOrderReservation reservation) {
        // 만약 매수 예약 주문 + 잠겨있는 예수금이 0보다 크면, 해당 증권 계좌의 CashBalance에서 잠겨있는 예수금을 해제한다.
        if (reservation.getSide() == StockOrderSide.BUY && reservation.getReservedCashAmount().compareTo(BigDecimal.ZERO) > 0) {
            findCashBalanceForUpdate(reservation.getInvestment().getId(), reservation.getCurrencyCode())
                    .releaseLocked(reservation.getReservedCashAmount());
            return;
        }
        // 만약 매도 예약 주문 + 잠겨있는 주식 수량이 0보다 크면, 해당 증권 계좌가 보여중인 종목 수(StockHolding)에서 잠겨있는 주식 수량을 해제한다.
        if (reservation.getSide() == StockOrderSide.SELL && reservation.getReservedStockQuantity().compareTo(BigDecimal.ZERO) > 0) {
            stockHoldingRepository.findByInvestmentIdAndStockIdForUpdate(
                            reservation.getInvestment().getId(),
                            reservation.getStock().getId())
                    .orElseThrow(() -> new RuntimeException("보유 종목이 없습니다."))
                    .releaseLockedQuantity(reservation.getReservedStockQuantity());
        }
    }
    // InvestmentCashBalance에서 특정 증권계좌의 특정 통화 잔고를 리턴하는 메서드
    InvestmentCashBalance findCashBalanceForUpdate(Long investmentId, CurrencyCode currencyCode) {
        return investmentCashBalanceRepository.findByInvestmentIdAndCurrencyCodeForUpdate(investmentId, currencyCode)
                .orElseThrow(() -> new RuntimeException(currencyCode.name() + " 예수금 잔고가 없습니다."));
    }
    // StockHolding에서 특정 증권 계좌가 특정 종목을 얼마나 가지고 있는지를 리턴하는 메서드
    StockHolding findOrCreateHoldingForUpdate(Investment investment, Stock stock, CurrencyCode currencyCode) {
        return stockHoldingRepository.findByInvestmentIdAndStockIdForUpdate(investment.getId(), stock.getId())
                .orElseGet(() -> stockHoldingRepository.save(StockHolding.create(investment, stock, currencyCode)));
    }

    // 특정 주문에 의해 잠금상태가 된 금액 또는 종목 수량을 저장하는 레코드
    record ReservedAsset(
            BigDecimal cashAmount,
            BigDecimal stockQuantity
    ) {
    }
}
