package com.finmate.controller.market;

import com.finmate.domain.market.dto.MarketReportResponse;
import com.finmate.service.market.news.MarketReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 시장 리포트 데이터를 JSON으로 제공한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market-reports")
public class MarketReportController {
    private final MarketReportService marketReportService;

    @GetMapping("/{topic}")
    public MarketReportResponse report(@PathVariable String topic) {
        // useMarketReport의 fetch 요청이 이 메서드에 도착한다.
        return marketReportService.getReport(topic);
    }
}
