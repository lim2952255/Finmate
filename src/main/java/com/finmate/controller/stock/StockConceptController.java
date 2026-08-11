package com.finmate.controller.stock;

import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.concept.StockConceptResponse;
import com.finmate.service.stock.concept.StockConceptQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// REST Controller의 경우에는 리턴값이 view의 이름이 아니라, HTTP 응답에 바로 담긴다.
@RestController
@RequestMapping("/api/stocks/{stockId}/concepts")
@RequiredArgsConstructor
public class StockConceptController {
    // DB에 저장되어 있는 개념카드 정보를 리턴한다.
    private final StockConceptQueryService conceptQueryService;

    @GetMapping("/{conceptCode}")
    public StockConceptResponse getConcept(
            @PathVariable Long stockId,
            @PathVariable StockConceptCode conceptCode
    ) {
        return conceptQueryService.getConcept(stockId, conceptCode);
    }
}
