package com.finmate.domain.stock.dto.concept;

import com.finmate.domain.stock.concept.StockConceptCode;

// 개념카드에 표시할 정보들을 모은 레코드
public record StockConceptResponse(
        StockConceptCode conceptCode,
        String title,
        String summary,
        StockConceptVisualResponse visual, // 시각자료
        StockConceptAnalysisResponse stockAnalysis, // 현재 종목의 실제 수치 기반 설명
        String detailedExplanation,
        String bakeryExample,
        String caution
) {
}
