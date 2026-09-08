package com.finmate.controller.stock;

import com.finmate.domain.stock.dto.disclosure.StockDisclosureResponse;
import com.finmate.service.stock.disclosure.StockDisclosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 종목 상세의 시황/공시 탭에 종목의 시황/공시정보와 해당 정보에 FinBERT 분석 결과를 붙인 데이터를 제공한다.
// 클라이언트가 종목 상세페이지에 진입하면, 프론트는 해당 API를 통해 백엔드에게 종목의 시황/공시정보를 요청한다.
// 백엔드에서는 종목의 마지막 시황/공시정보 갱신시각을 TTL과 비교하여 시황/공시정보를 갱신할지 여부를 결정하고, 종목의 시황/공시정보를 프론트에게 전달한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockId}/disclosures")
public class StockDisclosureController {
    private final StockDisclosureService disclosureService;

    // 저장 데이터가 오래된 경우 서비스 계층에서 KIS 갱신까지 수행한 뒤 최근 10건을 반환한다.
    @GetMapping
    public StockDisclosureResponse getDisclosures(@PathVariable Long stockId) {
        return disclosureService.getDisclosures(stockId);
    }
}
