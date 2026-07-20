package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 조회 / 검증용 클래스
@Service
@RequiredArgsConstructor
public class StockTradingLookupService {
    private final StockRepository stockRepository;
    private final InvestmentRepository investmentRepository;

    Stock findStock(Long stockId) {
        validateRequired(stockId, "종목은 필수입니다.");
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));
    }

    Investment findOwnedInvestmentForUpdate(Long userId, Long investmentId) {
        validateRequired(investmentId, "증권 계좌는 필수입니다.");
        Investment investment = investmentRepository.findByIdForUpdate(investmentId)
                .orElseThrow(() -> new RuntimeException("증권 계좌를 찾을 수 없습니다."));
        validateOwnedInvestment(userId, investment);
        return investment;
    }

    void validateOwnedInvestment(Long userId, Investment investment) {
        if (!investment.getUser().getId().equals(userId)) {
            throw new RuntimeException("본인 증권 계좌만 사용할 수 있습니다.");
        }
    }

    void validateTradable(Stock stock) {
        if (!stock.isActive() || !stock.isTradable() || stock.isTradingHalted()) {
            throw new RuntimeException("현재 주문할 수 없는 종목입니다.");
        }
    }

    // 현재 시간이 거래가능 시간대인지 확인
    void validateTradingTime(Stock stock) {
        if (!StockMarketSchedules.isTradingTime(stock.getMarketType(), ZonedDateTime.now())) {
            throw new RuntimeException("정규장 또는 장후 시간외 거래 시간에만 주문할 수 있습니다. 거래 가능 시간: "
                    + StockMarketSchedules.tradingTimeDescription(stock.getMarketType()));
        }
    }

    Investment selectInvestment(List<Investment> investments, Long investmentId) {
        if (investments == null || investments.isEmpty()) {
            return null;
        }

        if (investmentId != null) {
            return investments.stream()
                    .filter(investment -> investment.getId().equals(investmentId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("증권 계좌를 찾을 수 없습니다."));
        }

        return investments.stream()
                .filter(Investment::isPrimary)
                .findFirst()
                .orElse(investments.get(0));
    }

    CurrencyCode currencyCode(Stock stock) {
        try {
            return CurrencyCode.valueOf(stock.getCurrency());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("지원하지 않는 종목 통화입니다. currency=" + stock.getCurrency());
        }
    }

    StockOrderSide requireSide(StockOrderSide side) {
        validateRequired(side, "매수/매도 구분은 필수입니다.");
        return side;
    }

    StockOrderType requireOrderType(StockOrderType orderType) {
        validateRequired(orderType, "주문 유형은 필수입니다.");
        return orderType;
    }
}
