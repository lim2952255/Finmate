package com.finmate.domain.stock.dto.concept;

import java.util.List;

// 종목의 실제 수치로 구성한 개념 분석 정보를 담은 DTO
public record StockConceptAnalysisResponse(
        boolean available, // 종목의 실제 데이터를 사용가능한지 여부
        String heading, // 화면에 표시할 제목
        List<Metric> metrics, // 카드안에 보여 핵심 수치정보들을 담은 리스트 (현재가, PER, PBR등)
        String formula, // 계산 공식
        String interpretation, // 숫자의 의미해석
        String reference, // 수치의 기준 정보 (2026년 2분기 등)
        String updatedAt, // 데이터 기준시각
        String source, // 데이터 출처
        String unavailableReason // 데이터를 제공할 수 없을때 화면에 보여줄 오류메시지
) {
    // record의 compact constructor
    // record는 원래 자동으로 생성자를 만들어주는데, 특정 제약을 추가하고 싶을때는 compact constructor를 사용할 수 있다.
    public StockConceptAnalysisResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    // 현재 데이터를 이용할 수 없는 경우에, 오류 메세지를 표시한다.
    public static StockConceptAnalysisResponse unavailable(String heading, String reason) {
        return new StockConceptAnalysisResponse(
                false, heading, List.of(), null, null, null, null, "한국투자증권 KIS", reason);
    }

    // 실제 종목의 수치정보가 담기는 레코드
    public record Metric(String label, String value) {
    }
}
