package com.finmate.evaluation.news;

import com.finmate.service.news.E5EmbeddingModel;
import com.finmate.service.news.EmbeddingNewsRankingStrategy;
import com.finmate.service.news.KeywordNewsRankingStrategy;
import com.finmate.service.news.KoreanTfIdfNewsRankingStrategy;
import com.finmate.service.news.TfIdfNewsRankingStrategy;

import java.util.List;

/**
 * 평가에 필요한 모델과 토크나이저, 임계값들을 모두 준비한다.
 *
 * 운영에서는 설정으로 전략 하나만 선택하지만 평가는 전략별 성능을 비교해야 하므로 여러 전략과 여러 threshold 조합을 동시에 만든다.
 *
 * 즉 모든 평가전략과 임계값 조합을 한번에 관리하고, 자원을 정리하는 역할을 수행한다.
 */
final class NewsEvaluationStrategySet implements AutoCloseable {
    // 환경변수로 모델 경로를 지정하지 않았을 때 프로젝트 루트에서 찾을 E5 파일이다.
    private static final String DEFAULT_MODEL_PATH =
            "models/multilingual-e5-small/model.onnx";
    private static final String DEFAULT_TOKENIZER_PATH =
            "models/multilingual-e5-small/tokenizer.json";
    // 유사도가 threshold 이상이면 이미 선택된 기사와 같은 내용으로 보고 제외한다.
    // 여러 값을 순회하면 중복 제거 강도에 따른 품질과 기사 수 변화를 비교할 수 있다.
    private static final List<Double> DEFAULT_TF_IDF_THRESHOLDS =
            List.of(0.20, 0.30, 0.40, 0.50);
    private static final List<Double> DEFAULT_KOREAN_TF_IDF_THRESHOLDS =
            List.of(0.20, 0.30, 0.40, 0.50);
    private static final List<Double> DEFAULT_EMBEDDING_THRESHOLDS =
            List.of(0.90, 0.92, 0.94, 0.96);

    // 각 한국어 TF-IDF 전략은 Lucene 형태소 분석기를 소유하므로 종료 시 모두 close해야 한다.
    private final List<KoreanTfIdfNewsRankingStrategy> koreanTfIdfStrategies;
    // 여러 임계값의 임베딩 전략이 무거운 ONNX 모델과 세션 하나를 공유한다.
    private final E5EmbeddingModel embeddingModel;
    // Runner가 실제로 순회할 전체 전략·임계값 조합의 불변 목록이다.
	// NewsStrategyVariant는 전략과 임계값을 담은 객체이다.
    private final List<NewsStrategyVariant> variants;

    NewsEvaluationStrategySet() {
        // 모든 novelty 전략은 먼저 키워드 전략이 만든 관련도 기준 순서를 공통 출발점으로 사용한다.
        KeywordNewsRankingStrategy keywordStrategy = new KeywordNewsRankingStrategy();
        // .env에 목록이 있으면 실험값을 바꾸고, 없으면 위 기본 threshold sweep을 사용한다.
        List<Double> tfIdfThresholds = EvaluationEnvironment.optionalDoubleList(
                "NEWS_EVALUATION_TF_IDF_THRESHOLDS",
                DEFAULT_TF_IDF_THRESHOLDS);
        List<Double> koreanTfIdfThresholds = EvaluationEnvironment.optionalDoubleList(
                "NEWS_EVALUATION_KOREAN_TF_IDF_THRESHOLDS",
                DEFAULT_KOREAN_TF_IDF_THRESHOLDS);
        List<Double> embeddingThresholds = EvaluationEnvironment.optionalDoubleList(
                "NEWS_EVALUATION_EMBEDDING_THRESHOLDS",
                DEFAULT_EMBEDDING_THRESHOLDS);

        // threshold마다 별도 전략 객체를 만든다. 같은 구현이라도 threshold가 다르면 결과가 달라진다.
        this.koreanTfIdfStrategies = koreanTfIdfThresholds.stream()
                .map(threshold -> new KoreanTfIdfNewsRankingStrategy(keywordStrategy, threshold))
                .toList();
        // E5는 전체 평가 시작 시 한 번만 로딩하고 모든 데이터셋과 embedding threshold가 공유한다.
        this.embeddingModel = new E5EmbeddingModel(
                EvaluationEnvironment.optional("NEWS_EMBEDDING_MODEL_PATH", DEFAULT_MODEL_PATH),
                EvaluationEnvironment.optional("NEWS_EMBEDDING_TOKENIZER_PATH", DEFAULT_TOKENIZER_PATH));

        // 모든 임계값 결과의 합집합을 한 번만 judge할 수 있도록 평가 구성을 한 목록에 고정한다.
        java.util.ArrayList<NewsStrategyVariant> configuredVariants = new java.util.ArrayList<>();
        // 키워드 전략에는 기사 간 유사도 계산이 없으므로 threshold를 null로 기록한다.
        configuredVariants.add(new NewsStrategyVariant(keywordStrategy, null));
        // 일반 공백 토큰 기반 TF-IDF를 threshold별로 추가한다.
        for (double threshold : tfIdfThresholds) {
            configuredVariants.add(new NewsStrategyVariant(
                    new TfIdfNewsRankingStrategy(keywordStrategy, threshold),
                    threshold));
        }
        // 위에서 만든 형태소 분석기 보유 객체와 해당 threshold 값을 같은 index로 묶는다.
        for (int index = 0; index < koreanTfIdfThresholds.size(); index++) {
            configuredVariants.add(new NewsStrategyVariant(
                    koreanTfIdfStrategies.get(index),
                    koreanTfIdfThresholds.get(index)));
        }
        // E5 세션은 공유하되 중복 판정 threshold가 다른 임베딩 전략을 각각 추가한다.
        for (double threshold : embeddingThresholds) {
            configuredVariants.add(new NewsStrategyVariant(
                    new EmbeddingNewsRankingStrategy(keywordStrategy, embeddingModel, threshold),
                    threshold));
        }
        this.variants = List.copyOf(configuredVariants);
    }

    List<NewsStrategyVariant> variants() {
        return variants;
    }

    @Override
    public void close() {
        // Runner가 try-with-resources를 빠져나올 때 Lucene 분석기와 ONNX 네이티브 자원을 정리한다.
        koreanTfIdfStrategies.forEach(KoreanTfIdfNewsRankingStrategy::close);
        embeddingModel.close();
    }
}
