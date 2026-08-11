package com.finmate.controller.learning;

import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.concept.StockConceptResponse;
import com.finmate.service.learning.InvestmentLearningCatalog;
import com.finmate.service.stock.concept.StockConceptQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 실제 카드 세부내용을 불러오는 RestController
@RestController
@RequestMapping("/api/investment-learning/concepts")
@RequiredArgsConstructor
public class InvestmentLearningConceptController {

    // DB에 저장된 개념 카드 내용을 가져오는 서비스
    private final StockConceptQueryService conceptQueryService;
    // 지원하는 투자학습개념코드 모음
    private final InvestmentLearningCatalog learningCatalog;

    @GetMapping("/{conceptCode}")
    public StockConceptResponse getConcept(@PathVariable StockConceptCode conceptCode) {
        if (!learningCatalog.supports(conceptCode)) {
            throw new IllegalArgumentException("지원하지 않는 투자 학습 개념 코드입니다: " + conceptCode);
        }
        return conceptQueryService.getConcept(conceptCode);
    }
}
