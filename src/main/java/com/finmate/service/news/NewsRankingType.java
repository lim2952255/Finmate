package com.finmate.service.news;

// 뉴스 후보를 정렬·선별하는 알고리즘을 구분하는 식별자
public enum NewsRankingType {
    // 발행 날짜와 제목·요약문에 포함된 주요 키워드 점수를 기준으로 정렬한다.
    KEYWORD,

    // TF-IDF 기반 문서 유사도를 활용해 관련성과 중복도를 판단한다.
    TF_IDF,

    // 한국어 형태소 분석(Nori)을 적용한 TF-IDF 유사도로 중복도를 판단한다.
    KOREAN_TF_IDF,

    // 임베딩 기반 의미 유사도를 활용해 관련성과 새로운 정보를 판단한다.
    EMBEDDING
}
