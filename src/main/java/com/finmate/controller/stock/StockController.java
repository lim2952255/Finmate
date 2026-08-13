package com.finmate.controller.stock;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 종목 화면의 React 진입 URL과 시장 순위 데이터 조회를 연결한다.
@Controller
@RequestMapping("/investments/stocks")
public class StockController {

    @GetMapping({"/search", "/watchlist", "/detail", "/market-movers"})
    public String reactEntry() {
        return "forward:/react/index.html";
    }
}
