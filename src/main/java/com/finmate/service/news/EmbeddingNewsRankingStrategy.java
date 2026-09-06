	package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * multilingual-e5-small 임베딩모델을 활용하여 문장 임베딩의 의미 유사도로 중복 기사를 제외하는 전략이다.
 * 키워드 전략이 만든 관련성 순서를 유지하면서 제목과 네이버 요약문을 함께 임베딩하므로,
 * 사용 단어가 달라도 같은 사건을 설명하는 기사를 의미 중심으로 비교할 수 있다.
 */
@Lazy // @Lazy는 해당 빈을 실제 사용시점까지 미루고 프록시 객체를 생성한다. 해당 전략은 임베딩모델을 로딩하는 비용이 크기 때문에 해당 빈을 생성하는 시점을 최대한 미룬다.
@Service
public class EmbeddingNewsRankingStrategy implements NewsRankingStrategy {
    // 오프라인 평가에서 품질과 novelty의 균형이 좋았던 값을 운영 기본 임계값으로 고정한다.
    private static final double SIMILARITY_THRESHOLD = 0.92;
    // E5 모델은 입력 쿼리 앞에 query: 를 붙이도록 학습되어 있기 때문에, 뉴스에 붙일 prefix를 등록한다.
    private static final String E5_SYMMETRIC_PREFIX = "query: ";
	// HTML 태그를 찾는다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
	// 하나 이상 연속된 공백 문자를 찾는다.
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final KeywordNewsRankingStrategy keywordNewsRankingStrategy; // 키워드 기반 랭킹 전략
    private final E5EmbeddingModel embeddingModel; // 사용할 임베딩 모델
    private final double similarityThreshold; // 현재 전략 인스턴스가 사용하는 임베딩 유사도 임계치

    @Autowired
    public EmbeddingNewsRankingStrategy(
            KeywordNewsRankingStrategy keywordNewsRankingStrategy,
            E5EmbeddingModel embeddingModel) {
        this(keywordNewsRankingStrategy, embeddingModel, SIMILARITY_THRESHOLD);
    }

    // 전략 평가에서 운영 기본값을 변경하지 않고 여러 유사도 임계값을 비교하기 위해 활용한다.
    public EmbeddingNewsRankingStrategy(
            KeywordNewsRankingStrategy keywordNewsRankingStrategy,
            E5EmbeddingModel embeddingModel,
            double similarityThreshold) {
        // cosine similarity의 유효 범위를 벗어난 설정은 초기화 시 즉시 차단한다.
        if (similarityThreshold < -1.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("임베딩 유사도 임계값은 -1.0 이상 1.0 이하여야 합니다.");
        }
        this.keywordNewsRankingStrategy = keywordNewsRankingStrategy;
        this.embeddingModel = embeddingModel;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public NewsRankingType type() {
        return NewsRankingType.EMBEDDING;
    }

    /**
     * 키워드 랭킹 1등은 그대로 선택하고 2등부터 선택된 모든 기사와의 최대 유사도를 검사한다.
     * 최대 유사도가 설정 임계값보다 낮은 후보만 novelty가 있다고 판단한다.
     */
    @Override
    public List<NewsItem> rank(List<NewsItem> candidates, List<String> keywords, int limit) {
        if (limit == 0 || candidates.isEmpty()) {
            return List.of();
        }

		// 키워드 기반 랭킹 전략을 기반으로 뉴스 기사들을 랭킹화한다.
        List<NewsItem> rankedCandidates = keywordNewsRankingStrategy.rank(
                candidates,
                keywords,
                candidates.size());
		// 각 candidates들에 대해서 createEmbeddingInput 메서드를 호출해서 임베딩모델에 input으로 넣기 위한 형태로 변환한다.
        List<String> embeddingInputs = rankedCandidates.stream()
                .map(this::createEmbeddingInput)
                .toList();
        // 후보 전체를 암베딩모델에 넣어 한번에 임베딩한다.
        List<float[]> embeddings = embeddingModel.embed(embeddingInputs);

        List<NewsItem> selected = new ArrayList<>();
        List<Integer> selectedIndexes = new ArrayList<>();

		// 키워드 기반 랭킹 순위에서 1등인 기사를 선택하며, 해당 기사는 앞으로 선택할 다음 기사들의 기준이 된다.
        selected.add(rankedCandidates.get(0));
        selectedIndexes.add(0);

        for (int candidateIndex = 1;
             candidateIndex < rankedCandidates.size() && selected.size() < limit;
             candidateIndex++) {
			// 현재 후보 기사의 임베딩과 이미 선택되어 있는 기사들의 임베딩 유사도중 최댓값을 계산한다.
            double maxSimilarity = maxSimilarity(candidateIndex, selectedIndexes, embeddings);
            // 선택된 기사 어느 것과도 임계값 이상으로 유사하지 않은 후보만 novelty가 있다고 판단한다.
            if (maxSimilarity < similarityThreshold) {
                selected.add(rankedCandidates.get(candidateIndex));
                selectedIndexes.add(candidateIndex);
            }
        }
        return List.copyOf(selected);
    }

    // 전체 본문을 크롤링하지 않고 네이버 API가 제공하는 제목과 description을 결합한다.
    private String createEmbeddingInput(NewsItem item) {
        String title = normalizeText(item.title());
        String description = normalizeText(item.description());
        // E5는 문장 대 문장 비교 같은 대칭 작업에서 모든 입력에 query 접두사를 사용한다.
		// 따라서 현재 구조에서는 두 뉴스를 대칭적으로 비교하기 때문에 모든 뉴스에 query 접두사를 붙인다.
        return E5_SYMMETRIC_PREFIX + title + ". " + description;
    }

    // HTML 강조 태그와 엔티티를 제거하고 연속 공백을 하나로 합쳐 모델 입력을 안정화한다.
    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
		// html 태그를 제거한다.
        String withoutTags = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
		// html 엔티티를 실제 문자로 변환한다. &amp; → & 와 같이 의미를 살리기 위해.
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
		// 긴 공백들을 하나의 공백문자르 치환한다.
        return WHITESPACE_PATTERN.matcher(unescaped).replaceAll(" ").trim();
    }

    // 현재 후보와 이미 선택된 모든 기사 간 cosine similarity의 최댓값을 구한다.
    private double maxSimilarity(int candidateIndex,
                                 List<Integer> selectedIndexes,
                                 List<float[]> embeddings) {
        double maxSimilarity = -1.0;
        for (int selectedIndex : selectedIndexes) {
            maxSimilarity = Math.max(
                    maxSimilarity,
                    dotProduct(embeddings.get(candidateIndex), embeddings.get(selectedIndex)));
        }
        return maxSimilarity;
    }

    // E5EmbeddingModel이 벡터를 L2 정규화하므로 내적이 곧 cosine 유사도다.
    private double dotProduct(float[] first, float[] second) {
        if (first.length != second.length) {
            throw new IllegalArgumentException("임베딩 벡터 차원이 서로 다릅니다.");
        }
        double dotProduct = 0.0;
        for (int index = 0; index < first.length; index++) {
            dotProduct += first[index] * second[index];
        }
        return dotProduct;
    }
}
