package com.finmate.controller.market;

import com.finmate.domain.market.dto.MarketReportResponse;
import com.finmate.domain.market.news.MarketReportTopic;
import com.finmate.service.market.news.MarketReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

// 시장/레포트 페이지에 대한 접속을 관리하고, API 요청을 처리하는 컨트롤러
@Controller
@RequiredArgsConstructor
public class MarketReportController {
    private final MarketReportService marketReportService;

    @GetMapping("/investments/reports")
    public String reports(Model model) {
        model.addAttribute("marketReportTopics", MarketReportTopic.values());
        return "investments/reports/index";
    }

    @ResponseBody
    @GetMapping("/api/market-reports/{topic}")
    public MarketReportResponse report(@PathVariable String topic) {
        return marketReportService.getReport(topic);
    }
}
