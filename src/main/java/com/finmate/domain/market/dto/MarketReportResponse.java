package com.finmate.domain.market.dto;

import com.finmate.domain.news.dto.NewsItem;

import java.time.LocalDateTime;
import java.util.List;

// 시장/테마 단위의 뉴스 기사 응답을 포른트에 전달하기 위한 DTO
public record MarketReportResponse(
        String topic, // 어떤 주제인지
        String displayName, // 프론트에 표시할 주제명
        String symbol, // 프론트에 표시할 주제별 UI 표현용 값
        String category, // 해당 주제의 성격을 나태내는 분류명 (지수, 환율, 금리)
        String description, // 해당 주제에 대한 설명
        String query, // 네이버 뉴스 API를 활용하여 검색할 때 실제로 사용한 검색어
        LocalDateTime updatedAt, // 마지막으로 캐시를 갱신한 시각
        List<String> keywords, // 기사별 score를 계산하는데 사용한 키워드
        List<NewsItem> items // 최종적으로 선별된 Top-N개의 기사목록
) {
    public MarketReportResponse {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
