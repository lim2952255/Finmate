package com.finmate.controller.learning;

import com.finmate.domain.learning.InvestmentLearningCatalogResponse;
import com.finmate.domain.learning.InvestmentLearningCategory;
import com.finmate.service.learning.InvestmentLearningCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

// React의 useLearningCatalog가 요청하는 카테고리와 개념 요약을 JSON으로 반환한다.
@RestController
@RequestMapping("/api/investment-learning/catalog")
@RequiredArgsConstructor
public class InvestmentLearningCatalogController {

    private final InvestmentLearningCatalog learningCatalog;

    @GetMapping
    public InvestmentLearningCatalogResponse getCatalog() {
        // enum 카테고리와 기존 학습 카탈로그의 개념 목록을 React가 쓰기 좋은 한 응답으로 묶는다.
        return new InvestmentLearningCatalogResponse(
                Arrays.stream(InvestmentLearningCategory.values())
                        .map(InvestmentLearningCatalogResponse.CategoryResponse::from)
                        .toList(),
                learningCatalog.getConcepts()
        );
    }
}
