package com.finmate.evaluation.news;

import com.finmate.domain.news.dto.NewsItem;
import com.finmate.service.news.NewsRankingType;

import java.util.List;

/**
 * 한 데이터셋에 한 전략을 실행해 얻은 Top 10과 순수 랭킹 처리 시간을 보관한다.
 * NewsStrategyVariant가 실행 전 임계값과 전략을 저장한다면,NewsStrategyRun은 실행 후 최종선택된 10개 기사와 측정 시간까지 저장한다.
 */
record NewsStrategyRun(
        NewsRankingType strategy, // KEYWORD, TF_IDF, KOREAN_TF_IDF, EMBEDDING
        Double similarityThreshold, // 실행에 사용한 임계값; KEYWORD는 null
        List<NewsItem> items, // 해당 구성에서 최종 선택된 최대 10개 기사
        long elapsedMillis // 외부 API와 Judge 시간을 제외한 rank(...) 실행 시간(총 걸린 시간)
) {
    // 이후 코드가 결과 목록을 바꾸지 못하게 불변 복사본으로 보관한다.
    NewsStrategyRun {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
