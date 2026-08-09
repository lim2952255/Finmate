package com.finmate.service.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCode;

// 개념정보를 담은 레코드
public record StockConceptSeedDefinition(
        StockConceptCode conceptCode, // 개념 카드 코드
        String title, // 개념 카드 제목
        String summary, // 개념 요약
        String detailedExplanation, // 개념 상세 설명
        String bakeryExample, // 빵집 예시
        String caution // 해당 개념을 이해할때 주의사항
) {
}
