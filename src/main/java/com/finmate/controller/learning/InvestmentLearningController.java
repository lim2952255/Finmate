package com.finmate.controller.learning;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 투자학습 탭에 접근하는 컨트롤러
@Controller
public class InvestmentLearningController {

    @GetMapping("/investment-learning")
    public String investmentLearning() {
        // 새로고침이나 주소 직접 입력 때만 Spring이 공통 React HTML을 전달한다.
        // React 화면 안에서 Link로 이동할 때는 이 메서드를 다시 호출하지 않는다.
        return "forward:/react/index.html";
    }
}
