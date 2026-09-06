package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 기본 TF-IDF와 한국어 형태소 TF-IDF가 공유하는 벡터 계산 및 novelty 선별 골격이다.
 * 후보 우선순위, TF-IDF 계산, cosine similarity 비교 방식은 이 클래스에서 동일하게 적용한다.
 */
abstract class AbstractTfIdfNewsRankingStrategy implements NewsRankingStrategy {

    private final KeywordNewsRankingStrategy keywordNewsRankingStrategy; // 키워드 기반 랭킹 전략
    private final double similarityThreshold; // 유사한 뉴스를 필터링하기 위한 유사도 임계치

    protected AbstractTfIdfNewsRankingStrategy(
            KeywordNewsRankingStrategy keywordNewsRankingStrategy,
            double similarityThreshold) {
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("TF-IDF 유사도 임계값은 0.0 이상 1.0 이하여야 합니다.");
        }
        // 하위 전략이 정한 임계값 이상이면 이미 선택한 기사와 중복된 것으로 판단한다.
        this.keywordNewsRankingStrategy = keywordNewsRankingStrategy;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 키워드 전략의 1등을 먼저 선택하고 다음 후보부터 이미 선택된 모든 기사와 비교한다.
     * 이후 후보의 코사인 유사도가 임계값보다 낮을 때만 결과에 추가한다.
     */
    @Override
    public final List<NewsItem> rank(List<NewsItem> candidates, List<String> keywords, int limit) {
        if (limit == 0 || candidates.isEmpty()) {
            return List.of();
        }

		// 키워드 전략을 통해 각 뉴스를 랭킹화한다.
        List<NewsItem> rankedCandidates = keywordNewsRankingStrategy.rank(
                candidates,
                keywords,
                candidates.size());

		// 모든 뉴스에 대해 각각 TF-IDF 벡터를 생성한다.
        List<TfIdfVector> vectors = createVectors(rankedCandidates);

		// 선택된 뉴스와 인덱스를 담는 List를 생성한다.
        List<NewsItem> selected = new ArrayList<>();
        List<Integer> selectedIndexes = new ArrayList<>();

        // 키워드 전략을 통해 가장 높은 우선순위에 위치한 기사를 첫번째로 선택하며, 다음 모든 기사후보들의 기준이 된다.
        selected.add(rankedCandidates.get(0));
        selectedIndexes.add(0);

        for (int candidateIndex = 1;
             candidateIndex < rankedCandidates.size() && selected.size() < limit;
             candidateIndex++) {
			// 현재 후보 기사의 TF-IDF 벡터와, 이미 선택된 기사들의 TF-IDF 벡터들간의 코사인 유사도중 최댓값을 반환한다.
            double maxSimilarity = maxSimilarity(candidateIndex, selectedIndexes, vectors);
            // 최대 코사인 유사도가 유사도 임계치를 넘지 않는 경우에만 기사를 선택하고, 넘는 경우에는 제외한다.
            if (maxSimilarity < similarityThreshold) {
                selected.add(rankedCandidates.get(candidateIndex));
                selectedIndexes.add(candidateIndex);
            }
        }
        return List.copyOf(selected);
    }

    // 제목의 중요도를 높이기 위해 제목 토큰은 두 번, 요약문 토큰은 한 번 반영한다.
    private List<String> tokenize(NewsItem item) {
        List<String> tokens = new ArrayList<>();
        List<String> titleTokens = tokenizeText(item.title()); // 제목을 토큰화 한다.
        tokens.addAll(titleTokens); // 제목 토큰은 두 번 반영한다.
        tokens.addAll(titleTokens);
        tokens.addAll(tokenizeText(item.description())); // 요약 토큰은 한번만 반영한다.
        return tokens;
    }


	// 토큰화 방법은 하위 클래스(기본 TF-IDF와 Nori 기반 한국어 형태소 기반 TF-IDF)에서 구현한다.
    protected abstract List<String> tokenizeText(String text);

    // 전체 후보를 문서 집합으로 보고 공통 DF를 계산한 뒤 각 문서의 TF-IDF 벡터를 생성한다.
    private List<TfIdfVector> createVectors(List<NewsItem> candidates) {
        List<List<String>> documents = candidates.stream()
                .map(this::tokenize) // 모든 문서들을 토큰화해서 리스트화한다.
                .toList();
		// DF를 계산한다. DF(t)= 단어 t가 등장한 문서 개수
        Map<String, Integer> documentFrequencies = calculateDocumentFrequencies(documents);

        return documents.stream()
                .map(tokens -> createVector(tokens, documentFrequencies, documents.size()))
                .toList(); // 각 문서마다 TF-IDF를 계싼한다.
    }

    // 한 문서 안에서 같은 토큰이 반복되어도 문서 빈도(DF)는 한 번만 증가시킨다.
    private Map<String, Integer> calculateDocumentFrequencies(List<List<String>> documents) {
        Map<String, Integer> frequencies = new HashMap<>(); // 특정 토큰이 등장한 문서의 수를 담는 Map이다.
        for (List<String> tokens : documents) { // 뉴스를 하나씩 조회한다.
            for (String token : new HashSet<>(tokens)) { // tokens를 리스트로 순회하는 것이 아니라, 중복된 토큰을 제거하면서 조회한다.
                frequencies.merge(token, 1, Integer::sum); // 기존에 값이 없으면 1, 있으면 기존값 + 1을 수행한다.
            }
        }
        return frequencies;
    }

    // 한 기사의 TF와 전체 후보 집합의 IDF를 곱해 TF-IDF 벡터를 생성한다.
    private TfIdfVector createVector(List<String> tokens, // 토큰 리스트
                                     Map<String, Integer> documentFrequencies, // DF 점수
                                     int documentCount) { // 문서 수
        if (tokens.isEmpty()) {
            return TfIdfVector.EMPTY;
        }

        Map<String, Integer> termCounts = new HashMap<>(); // 현재 문서에서 각 단어가 몇번 나왔는지를 담는다 (TF)
        for (String token : tokens) {
            termCounts.merge(token, 1, Integer::sum);
        }

        Map<String, Double> weights = new HashMap<>(); // 최종 TF-IDF 값을 계산한다.
        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) { // 뉴스에 등장한 각 토큰을 순차적으로 처리한다.
			// 토큰별로 TF값을 계산한다.
            double termFrequency = entry.getValue() / (double) tokens.size(); // 뉴스에서 특정 토큰이 등장한 횟수 / 뉴스 내 전체 토큰 수로 나눠 TF값을 계산한다.
           // 해당 토큰의 DF값(해당 토큰이 전체 문서에서 몇번 등장했는지)를 계산한다.
            int documentFrequency = documentFrequencies.getOrDefault(entry.getKey(), 0);
            // smoothing으로 0으로 나누는 것을 피하고 작은 후보 집합에서 IDF가 과도해지는 것을 완화한다.
			// 실제 IDF 값을 계산한다: log((전체 문서수 + 1) / (토큰이 등장한 문서수 + 1)) + 1
            double inverseDocumentFrequency = Math.log(
                    (documentCount + 1.0) / (documentFrequency + 1.0)) + 1.0;
            weights.put(entry.getKey(), termFrequency * inverseDocumentFrequency); // 각 토큰별 TF * IDF 값을 계산한다.
        }

        // TF-IDF 벡터의 크기값도 계산한다.
        double norm = Math.sqrt(weights.values().stream()
                .mapToDouble(weight -> weight * weight)
                .sum());
		// TF-IDF 벡터를 생성한다.
        return new TfIdfVector(Map.copyOf(weights), norm);
    }

    // 한 후보와 이미 선택된 모든 기사 사이의 유사도 중 가장 큰 값을 반환한다.
    private double maxSimilarity(int candidateIndex,
                                 List<Integer> selectedIndexes,
                                 List<TfIdfVector> vectors) {
        double maxSimilarity = 0.0;
        for (int selectedIndex : selectedIndexes) {
			// 후보 기사의 TF-IDF 벡터와 선택된 기사들의 TF-IDF 벡터들의 코사인유사도들 중 최댓값을 반환한다.
            maxSimilarity = Math.max(
                    maxSimilarity,
                    cosineSimilarity(vectors.get(candidateIndex), vectors.get(selectedIndex)));
        }
        return maxSimilarity;
    }

    // TF-IDF 벡터들의 코사인 유사도를 계산한다. cosSIM(A,B) = (A*B / |A||B|)
    private double cosineSimilarity(TfIdfVector first, TfIdfVector second) {
		// 벡터 하나의 크기가 0이면 토큰이 하나도 없는 경우이니까 0을 리턴한다.
        if (first.norm() == 0.0 || second.norm() == 0.0) {
            return 0.0;
        }
		// 두 벡터중 TF-IDF 단어 개수가 더 적은 벡터를 찾는다.
        boolean firstIsSmaller = first.weights().size() <= second.weights().size();
        Map<String, Double> smaller = firstIsSmaller
                ? first.weights()
                : second.weights();
        Map<String, Double> larger = firstIsSmaller
                ? second.weights()
                : first.weights();

        double dotProduct = 0.0; // 두 벡터간의 dotProduct값을 계산한다.
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
			// TF-IDF 단어가 작은 벡터의 토큰을 순회하면 더 효율적으로 내적을 계산할 수 있다.
            dotProduct += entry.getValue() * larger.getOrDefault(entry.getKey(), 0.0);
        }
        return dotProduct / (first.norm() * second.norm()); // 코사인유사도를 계산한다.
    }

    // 토큰별 TF-IDF 가중치와 미리 계산한 L2 norm을 불변 값으로 보관한다.
    private record TfIdfVector(Map<String, Double> weights, double norm) {
        private static final TfIdfVector EMPTY = new TfIdfVector(Map.of(), 0.0);
    }
}
