package com.finmate.controller.stock;

import com.finmate.domain.stock.dto.news.StockNewsResponse;
import com.finmate.service.stock.news.StockNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 종목별 뉴스 탭에서 종목별 뉴스 정보를 제공하는 REST Controller
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockId}/news")
public class StockNewsController {
    private final StockNewsService stockNewsService;

    @GetMapping
    public StockNewsResponse getNews(@PathVariable Long stockId) {
        return stockNewsService.getNews(stockId);
    }
}
