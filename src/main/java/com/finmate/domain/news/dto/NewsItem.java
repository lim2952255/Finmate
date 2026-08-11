package com.finmate.domain.news.dto;

// 네이버 뉴스 API로부터 받은 뉴스정보를 담는 DTO
public record NewsItem(
        String title, // 기사 제목
        String originalLink, // 해당 언론사의 원본 기사 링크
        String link, // 네이버 뉴스가 제공하는 기사 링크
        String description, // 기사내용의 일부분을 보여주는 요약문
        String publishedAt // 기사 발행 시각
) {
}
