package com.finmate.controller.learning;

import com.finmate.domain.learning.InvestmentLearningCategory;
import com.finmate.service.learning.InvestmentLearningCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 투자학습 탭에 접근하는 컨트롤러
@Controller
@RequiredArgsConstructor
public class InvestmentLearningController {
    // 투자학습 탭에 표시할 카드내용들을 저장하는 클래스
    private final InvestmentLearningCatalog learningCatalog;

    @GetMapping("/investment-learning")
    public String investmentLearning(Model model) {
        // 투자학습 카테고리 모음
        model.addAttribute("categories", InvestmentLearningCategory.values());
        model.addAttribute("concepts", learningCatalog.getConcepts());
        return "learning/investment-learning";
    }
}
