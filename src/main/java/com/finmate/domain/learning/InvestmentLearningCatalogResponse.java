package com.finmate.domain.learning;

import java.util.List;

// JSON의 { categories: [...], concepts: [...] } 구조를 정의하는 API 응답 record다.
public record InvestmentLearningCatalogResponse(
        List<CategoryResponse> categories, // 카테고리 목록
        List<InvestmentLearningConceptSummary> concepts // 개념목록
) {
    public InvestmentLearningCatalogResponse {
        // 외부에서 받은 목록을 복사해 응답 객체가 생성된 뒤 변경되지 않도록 한다.
        categories = List.copyOf(categories);
        concepts = List.copyOf(concepts);
    }

    public record CategoryResponse(
            String code,
            String displayName,
            String description
    ) {
        public static CategoryResponse from(InvestmentLearningCategory category) {
            // Java enum을 React가 바로 표시할 수 있는 코드·이름·설명 객체로 변환한다.
            return new CategoryResponse(
                    category.name(),
                    category.getDisplayName(),
                    category.getDescription()
            );
        }
    }
}
