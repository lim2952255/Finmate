package com.finmate.domain.stock.dto.trading;

import com.finmate.domain.investment.CurrencyCode;

import java.math.BigDecimal;

// 포트폴리오 페이지에서 특정 업종이 차지하는 비중 표현하는 DTO
public record StockPortfolioIndustryAllocation(
        CurrencyCode currencyCode,
        String groupName,
        String industryName,
        BigDecimal purchaseAmount,
        BigDecimal percentage
) {
}
