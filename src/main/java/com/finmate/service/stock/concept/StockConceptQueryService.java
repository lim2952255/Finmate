package com.finmate.service.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCard;
import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.concept.StockConceptResponse;
import com.finmate.repository.stock.concept.StockConceptCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockConceptQueryService {
    private final StockConceptCardRepository conceptCardRepository; // 개념카드의 정적 개념 조회
    private final StockConceptVisualCatalog conceptVisualCatalog; // 개념카드와 연관된 시각자료 조회
    private final StockConceptAnalysisService conceptAnalysisService; // 실제 종목의 데이터를 기반으로 개념설명

    // DB에서 해당 개념카드에 대한 개념정보를 조회해서 리턴한다.
    public StockConceptResponse getConcept(Long stockId, StockConceptCode conceptCode) {
        StockConceptCard conceptCard = conceptCardRepository
                .findByConceptCodeAndActiveTrue(conceptCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "개념정보를 찾을 수 없습니다: " + conceptCode
                ));

        // 개념 카드정보를 기반으로 StockConceptResponse DTO에 담아서 프론트에 전달한다.
        return toResponse(stockId, conceptCard);
    }

    // 투자 학습 화면에서는 종목 데이터 없이 DB의 정적 개념과 시각자료만 조회한다.
    public StockConceptResponse getConcept(StockConceptCode conceptCode) {
        StockConceptCard conceptCard = conceptCardRepository
                .findByConceptCodeAndActiveTrue(conceptCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "개념정보를 찾을 수 없습니다: " + conceptCode
                ));

        return new StockConceptResponse(
                conceptCard.getConceptCode(),
                conceptCard.getTitle(),
                conceptCard.getSummary(),
                conceptVisualCatalog.getVisual(conceptCard.getConceptCode()),
                null,
                conceptCard.getDetailedExplanation(),
                conceptCard.getBakeryExample(),
                conceptCard.getMarketImpact(),
                conceptCard.getCaution()
        );
    }

    private StockConceptResponse toResponse(Long stockId, StockConceptCard conceptCard) {
        return new StockConceptResponse(
                conceptCard.getConceptCode(),
                conceptCard.getTitle(),
                conceptCard.getSummary(),
                conceptVisualCatalog.getVisual(conceptCard.getConceptCode()), // 시각화자료 추가
                conceptAnalysisService.analyze(stockId, conceptCard.getConceptCode()), // 실제 종목의 개념정보를 조회한다.
                conceptCard.getDetailedExplanation(),
                conceptCard.getBakeryExample(),
                conceptCard.getMarketImpact(),
                conceptCard.getCaution()
        );
    }
}
