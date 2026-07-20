package com.finmate.service.stock.trading;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.trading.StockOrderPageInfo;
import com.finmate.domain.stock.dto.trading.StockPortfolioPageInfo;
import com.finmate.domain.stock.dto.trading.StockTradingHistoryPageInfo;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.stock.trading.StockHoldingRepository;
import com.finmate.repository.stock.trading.StockOrderRepository;
import com.finmate.repository.stock.trading.StockOrderReservationRepository;
import com.finmate.repository.stock.trading.StockTradeTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 주문 페이지, 포트폴리오, 주문/체결내역 조회용 페이지에 필요한 정보들을 DTO에 담아서 반환하는 서비스
@Service
@RequiredArgsConstructor
public class StockTradingQueryService {
    private final InvestmentRepository investmentRepository;
    private final StockHoldingRepository stockHoldingRepository;
    private final StockOrderRepository stockOrderRepository;
    private final StockOrderReservationRepository stockOrderReservationRepository;
    private final StockTradeTransactionRepository stockTradeTransactionRepository;
    private final StockTradingRealtimePriceService realtimePriceService; // 실시간 체결기준 가격을 결정하는 서비스
    private final StockTradingLookupService lookupService;

    // 주문 페이지 정보를 DTO에 담아서 리턴
    @Transactional(readOnly = true)
    public StockOrderPageInfo getOrderPageInfo(Long userId, Long stockId, Long investmentId) {
        Stock stock = lookupService.findStock(stockId);
        List<Investment> investments = investmentRepository.findByUserIdWithCashBalances(userId); // 사용자가 보유중인 증권 계좌 목록 + 각 계좌의 예수금 잔고
        Investment selectedInvestment = lookupService.selectInvestment(investments, investmentId); // 화면에서 선택된 증권계좌
        List<StockHolding> holdings = stockHoldingRepository.findOrderHoldingsByUserIdAndStockId(userId, stock.getId()); // 사용자의 각 증권 계좌가 해당 종목을 몇 주 가지고 있는지를 나타낸다.
        // 각 증권 계좌와 보유 종목 수를 Map으로 매핑
        Map<Long, StockHolding> holdingsByInvestmentId = holdings.stream()
                .collect(Collectors.toMap(
                        stockHolding -> stockHolding.getInvestment().getId(),
                        Function.identity()
                ));
        StockHolding holding = selectedInvestment == null ? null : holdingsByInvestmentId.get(selectedInvestment.getId()); // 현재 선택된 증권 계좌의 보유 종목 수를 반환

        return new StockOrderPageInfo(
                stock,
                lookupService.currencyCode(stock),
                investments,
                selectedInvestment == null ? null : selectedInvestment.getId(),
                holding, // 선택된 증권계좌의 종목 보유 수량
                holdings, // 사용자의 모든 증권계좌마다 해당 종목의 보유 수량
                realtimePriceService.findExecutablePrice(stock, StockOrderSide.BUY).orElse(null), // 매수 기준 체결가
                realtimePriceService.findExecutablePrice(stock, StockOrderSide.SELL).orElse(null), // 메도 기준 체결가
                realtimePriceService.findCurrentTradePrice(stock).orElse(null), // 실시간 체결가
                StockMarketSchedules.isTradingTimeNow(stock.getMarketType()),
                StockMarketSchedules.tradingTimeDescription(stock.getMarketType()));
    }

    // 포트폴리오 페이지 정보를 DTO에 담아서 리턴
    @Transactional(readOnly = true)
    public StockPortfolioPageInfo getPortfolioPageInfo(Long userId, Long investmentId) {
        List<Investment> investments = investmentRepository.findByUserIdWithCashBalances(userId);
        boolean allAccounts = investmentId == null; // investmentId가 null이면 true, 아니면 false
        Investment selectedInvestment = allAccounts ? null : lookupService.selectInvestment(investments, investmentId); // allAccounts가 false이면 특정 증권계좌를 선택
        if (!allAccounts && selectedInvestment == null) {
            return new StockPortfolioPageInfo(investments, null, List.of(), false);
        }

        // allAccounts가 true이면 현재 사용자가 가지고 있는 모든 계좌의 종목들을 찾아서 리턴
        // allAccount가 false이면 특정 증권계좌가 가지고 있는 모든 종목들을 찾아서 리턴
        List<StockHolding> holdings = allAccounts
                ? stockHoldingRepository.findPortfolioHoldingsByUserId(userId)
                : stockHoldingRepository.findPortfolioHoldingsByInvestmentId(selectedInvestment.getId());

        return new StockPortfolioPageInfo(investments, selectedInvestment, holdings, allAccounts);
    }

    // 주식 거래내역 페이지 정보를 DTO에 담아서 리턴
    @Transactional(readOnly = true)
    public StockTradingHistoryPageInfo getTradingHistoryPageInfo(Long userId, Long investmentId) {
        List<Investment> investments = investmentRepository.findByUserIdWithCashBalances(userId);
        boolean allAccounts = investmentId == null; // investmentId가 null이면 true, 아니면 false
        Investment selectedInvestment = allAccounts ? null : lookupService.selectInvestment(investments, investmentId); // allAccounts가 false이면 특정 증권계좌를 선택
        if (investments.isEmpty()) {
            return new StockTradingHistoryPageInfo(investments, null, List.of(), List.of(), List.of(), allAccounts);
        }

        if (allAccounts) { // allAccounts가 true이면 사용자의 모든 증권 계좌의 주식 거래내역을 DTO에 담는다.
            return new StockTradingHistoryPageInfo(
                    investments,
                    null,
                    stockOrderRepository.findByUserIdOrderByCreatedAtDesc(userId), // 주식 일반 주문 내역
                    stockOrderReservationRepository.findByUserIdOrderByCreatedAtDesc(userId), // 주식 예약 주문 내역
                    stockTradeTransactionRepository.findByUserIdOrderByExecutedAtDesc(userId), // 주식 체결 내역
                    true);
        }

        if (selectedInvestment == null) {
            return new StockTradingHistoryPageInfo(investments, null, List.of(), List.of(), List.of(), false);
        }

        // allAccounts가 false이면 사용자의 특정 증권 계좌의 주식 거래내역을 DTO에 담는다.
        return new StockTradingHistoryPageInfo(
                investments,
                selectedInvestment,
                stockOrderRepository.findByInvestment_IdOrderByCreatedAtDesc(selectedInvestment.getId()), // 주식 일반 주문 내역
                stockOrderReservationRepository.findByInvestment_IdOrderByCreatedAtDesc(selectedInvestment.getId()), // 주식 예약 주문 내역
                stockTradeTransactionRepository.findByInvestment_IdOrderByExecutedAtDesc(selectedInvestment.getId()), // 주식 체결 내역
                false);
    }
}
