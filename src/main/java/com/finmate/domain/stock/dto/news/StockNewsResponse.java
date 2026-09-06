package com.finmate.domain.stock.dto.news;

import com.finmate.domain.news.dto.NewsItem;
import com.finmate.service.news.NewsRankingType;

import java.time.LocalDateTime;
import java.util.List;

// 특정 종목의 뉴스 조회 결과정보를 프론트엔드에 전달하기 위한 DTO
public record StockNewsResponse(
        Long stockId, // 종목 아이디
        String stockName, // 종목명
        String market, // 시장정보
        String query, // 검색 키워드
        LocalDateTime updatedAt, // 조회 시각
        List<String> keywords, // 종목을 검색하는데 사용한 키워드 목록
        NewsRankingType rankingType, // 이 응답을 생성할 때 사용한 단일 랭킹 전략
        List<NewsItem> items // 선택된 전략이 선별한 Top N 뉴스목록
) {
    public StockNewsResponse {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
