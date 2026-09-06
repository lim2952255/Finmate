package com.finmate.evaluation.news;

import com.finmate.service.news.NewsRankingStrategy;

/**
 * 동일 전략을 서로 다른 유사도 임계값으로 실행하기 위한 평가 전용 구성이다.
 * 키워드 전략은 유사도 임계값을 사용하지 않으므로 similarityThreshold가 null이다.
 *
 * 예를 들어 TF-IDF 0.20과 TF-IDF 0.40은 전략 종류는 같지만 서로 다른 평가 구성이다.
 * 이 레코드는 아직 실행하지 않은 {전략 객체 + 실험 파라미터}를 묶는다.
 */
record NewsStrategyVariant(
        NewsRankingStrategy strategy, // 평가에 사용할 전략(Keyword, TF-IDF, Nori + TF-IDF, Embedding)
        Double similarityThreshold // 유사도를 판단할 threshold. keyword 전략은 유사도를 판단하지 않기 때문에 null로 설정된다.
) {
}
