package com.finmate.domain.stock.dto.disclosure;

import com.finmate.domain.news.NewsSentiment;
import com.finmate.domain.stock.disclosure.StockDisclosure;

import java.time.LocalDateTime;

// 시황/공시 화면 카드 한 개에 필요한 제목, 자료원, 작성시각과 감성분석 결과만 노출한다.
// StockDisclosure에 저장되어 있는 모든 정보를 노출시키는 것이 아니라, 화면에 필요한 정보만 DTO에 담아 노출시킨다.
public record StockDisclosureItem(
        Long id,
        String title, // 제목
        String source, // 자료원
        LocalDateTime disclosedAt, // 작성일자
        NewsSentiment sentiment // 감성분석 결과
) {
    // 영속 엔티티가 API 밖으로 직접 노출되지 않도록 응답 DTO로 변환한다.
    public static StockDisclosureItem from(StockDisclosure disclosure) {
        return new StockDisclosureItem(
                disclosure.getId(),
                disclosure.getTitle(),
                disclosure.getSource(),
                disclosure.getDisclosedAt(),
                disclosure.getSentiment());
    }
}
