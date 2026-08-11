package com.finmate.domain.stock.dto.detail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 장중에는 투자자 수급이나 공매도, 대차거래 데이터가 수시로 변하기 때문에 이를 매번 DB에 저장하는것이 아니라, 스냅샷을 Redis에 캐싱하는 방식을 사용한다.
// 이후에 장 마감시, 또는 이후에 접속하게 되면, DB에 저장되어 있어야할 최신일자와, 현재 DB에 실제로 저장된 데이터일자를 비교하여 부족한 부분을 on-demand방식으로 채워넣는다.
public record DomesticStockDailyFlowSnapshot(
        LocalDate tradeDate,
        InvestorTrade investorTrade,
        ShortSale shortSale,
        LoanTransaction loanTransaction,
        LocalDateTime fetchedAt
) {
    // 투자자 수급
    public record InvestorTrade(
            BigDecimal closePrice,
            Long foreignBuyQuantity,
            Long foreignSellQuantity,
            Long foreignNetQuantity,
            Long personalBuyQuantity,
            Long personalSellQuantity,
            Long personalNetQuantity,
            Long institutionBuyQuantity,
            Long institutionSellQuantity,
            Long institutionNetQuantity
    ) {
    }

    // 일별 공매도 데이터
    public record ShortSale(
            BigDecimal closePrice,
            Long accumulatedVolume,
            Long shortSaleQuantity,
            BigDecimal shortSaleVolumeRatio,
            BigDecimal accumulatedTradeAmount,
            BigDecimal shortSaleTradeAmount,
            BigDecimal shortSaleTradeAmountRatio,
            BigDecimal averagePrice
    ) {
    }

    // 일별 대차거래 데이터
    public record LoanTransaction(
            BigDecimal closePrice,
            Long newLoanQuantity,
            Long redeemedLoanQuantity,
            Long loanBalanceQuantity,
            BigDecimal newLoanAmount,
            BigDecimal redeemedLoanAmount,
            BigDecimal loanBalanceAmount
    ) {
    }
}
