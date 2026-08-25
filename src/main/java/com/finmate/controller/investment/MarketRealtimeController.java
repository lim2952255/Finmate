package com.finmate.controller.investment;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.service.market.MarketRealtimeQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 프런트엔드가 표시할 최신 환율·지수 시세를 JSON으로 제공한다.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/market-data")
public class MarketRealtimeController {

    private final MarketRealtimeQuoteService marketRealtimeQuoteService;

    @GetMapping("/realtime")
    public ResponseEntity<MarketRealtimeMessage> marketDataRealtime(@RequestParam MarketIndicatorSymbol indicator) {
        return marketRealtimeQuoteService.getLatest(indicator)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
