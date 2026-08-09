package com.finmate.domain.stock.dto.concept;

// 개념 카드에 표시할 시각자료 정보
public record StockConceptVisualResponse(
        String assetPath,
        String altText,
        String caption
) {
}
