package com.finmate.controller.market;

import com.finmate.domain.market.dto.MarketReportResponse;
import com.finmate.service.market.news.MarketReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

// 시장/레포트 페이지에 대한 접속을 관리하고, API 요청을 처리하는 컨트롤러
@Controller
@RequiredArgsConstructor
public class MarketReportController {
    private final MarketReportService marketReportService;

    @GetMapping("/investments/reports")
    public String reports() {
        // 페이지 요청에는 React 진입 HTML을 주고, 아래 API 요청에는 JSON 데이터를 준다.
        return "forward:/react/index.html";
    }

    @ResponseBody
    @GetMapping("/api/market-reports/{topic}")
    public MarketReportResponse report(@PathVariable String topic) {
        // useMarketReport의 fetch 요청이 이 메서드에 도착한다.
        return marketReportService.getReport(topic);
    }
}
