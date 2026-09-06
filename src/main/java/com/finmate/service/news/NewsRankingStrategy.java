package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;

import java.util.List;

/**
 * 네이버 뉴스 API에서 조회한 후보를 정렬하고 중복을 제거하는 전략의 interface이다.
 * 다양한 전략 알고리즘들이 해당 인터페이스를 구현하기 때문에, 서비스코드를 수정하지 않고, 구현체만 수정함으로서 손쉽게 전략 교체가 가능하다.
 * */
public interface NewsRankingStrategy {

	// 캐시와 API 응답에서 이 구현체의 결과를 구분할 전략 타입을 반환한다.
    NewsRankingType type();

    /**
     * 후보 기사를 전략별 규칙으로 정렬·선별한다.
     *
     * @param candidates : 네이버 뉴스 API에서 조회한 원본 후보 목록
     * @param keywords : 투자 관련도를 판단할 때 사용하는 주요 키워드 목록
     * @param limit : 반환할 최대 기사 수
     * @return : 전략이 정한 우선순서로 정렬된 기사 목록
     */
    List<NewsItem> rank(List<NewsItem> candidates, List<String> keywords, int limit);
}
