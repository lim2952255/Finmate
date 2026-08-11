package com.finmate.domain.learning;

import com.finmate.domain.stock.concept.StockConceptCode;

// 투자학습 카드 관련 레코드
public record InvestmentLearningConceptSummary(
        StockConceptCode conceptCode,
        InvestmentLearningCategory category,
        String title,
        String summary
) {
}
