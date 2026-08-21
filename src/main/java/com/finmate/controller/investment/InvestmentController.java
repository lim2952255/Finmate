package com.finmate.controller.investment;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.service.market.MarketRealtimeQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

// 투자 영역의 React 진입 URL과 기존 실시간 시세 조회 경로를 연결한다.
@RequiredArgsConstructor
@Controller
@RequestMapping("/investments")
public class InvestmentController {

    private final MarketRealtimeQuoteService marketRealtimeQuoteService;

    @GetMapping({"", "/open", "/list", "/transfer", "/securityCashTransaction", "/currency-exchange",
            "/currency-exchange/transactions", "/portfolio", "/orders", "/market-data", "/exchanges", "/indices"})
    public String reactEntry() {
        return "forward:/react/index.html";
    }

    @ResponseBody
    @GetMapping("/market-data/realtime")
    public ResponseEntity<MarketRealtimeMessage> marketDataRealtime(@RequestParam MarketIndicatorSymbol indicator) {
        return marketRealtimeQuoteService.getLatest(indicator)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
