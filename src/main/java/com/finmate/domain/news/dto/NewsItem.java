package com.finmate.domain.news.dto;

import com.finmate.domain.news.NewsSentiment;

// 네이버 뉴스 API로부터 받은 뉴스정보를 담는 DTO
public record NewsItem(
        String title, // 기사 제목
        String originalLink, // 해당 언론사의 원본 기사 링크
        String link, // 네이버 뉴스가 제공하는 기사 링크
        String description, // 기사내용의 일부분을 보여주는 요약문
        String publishedAt, // 기사 발행 시각
        NewsSentiment sentiment // 뉴스의 호재·보통·악재 감성 분석 결과
) {
    // NAVER 원본 응답과 랭킹 단계에서는 아직 감성을 계산하지 않으므로 기존 생성 방식을 유지한다.
    public NewsItem(String title,
                    String originalLink,
                    String link,
                    String description,
                    String publishedAt) {
        this(title, originalLink, link, description, publishedAt, null);
    }

    // 원본 기사 정보는 그대로 유지하고 최종 선별 기사에만 감성 결과를 추가한다.
    public NewsItem withSentiment(NewsSentiment analyzedSentiment) {
        return new NewsItem(title, originalLink, link, description, publishedAt, analyzedSentiment);
    }
}
