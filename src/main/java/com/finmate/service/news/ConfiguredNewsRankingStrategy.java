package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 뉴스 랭킹 전략 선택기
 */
@Primary
@Service
public class ConfiguredNewsRankingStrategy implements NewsRankingStrategy {

    // 설정값으로 한 번 선택한 실제 전략을 애플리케이션 실행 중 계속 재사용한다.
    private final NewsRankingStrategy delegate;

    public ConfiguredNewsRankingStrategy(
            @Value("${finmate.news.ranking-strategy:KOREAN_TF_IDF}") NewsRankingType rankingType,
            KeywordNewsRankingStrategy keywordStrategy,
            TfIdfNewsRankingStrategy tfIdfStrategy,
            KoreanTfIdfNewsRankingStrategy koreanTfIdfStrategy,
            ObjectProvider<EmbeddingNewsRankingStrategy> embeddingStrategyProvider) {
        // ObjectProvider를 사용해 EMBEDDING을 선택한 경우에만 지연 빈과 ONNX 모델을 초기화한다.
		// 랭킹 전략 타입에 따라 특정 전략을 선택해서 실행한다.
        this.delegate = switch (rankingType) {
            case KEYWORD -> keywordStrategy;
            case TF_IDF -> tfIdfStrategy;
            case KOREAN_TF_IDF -> koreanTfIdfStrategy;
            case EMBEDDING -> embeddingStrategyProvider.getObject();
        };
    }

    @Override
    public NewsRankingType type() {
        // 캐시가 어떤 전략의 결과인지 기록할 수 있도록 실제 구현체의 타입을 그대로 노출한다.
        return delegate.type();
    }

    @Override
    public List<NewsItem> rank(List<NewsItem> candidates, List<String> keywords, int limit) {
        // 호출부가 설정 분기 없이 동일한 인터페이스로 선택된 전략을 실행하도록 위임한다.
        return delegate.rank(candidates, keywords, limit);
    }
}
