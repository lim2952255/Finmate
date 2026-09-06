package com.finmate.evaluation.news;

import com.finmate.domain.news.dto.NewsItem;
import com.finmate.service.news.NewsRankingType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM judge 라벨을 정답으로 사용해 관련성, 중복도, 유효 결과 수를 전략별로 계산한다.
 *
 * 한 점수만으로 전략을 고르면 관련 기사만 반복해서 보여 주거나, 중복을 지나치게 제거해 결과 10건을 채우지 못하는 문제를 놓칠 수 있다.
 * 그래서 순위 품질, 사건 중복, 유효 사건 수, 선택 개수와 처리 시간을 함께 기록한다.
 */
final class NewsRankingMetrics {
    // 운영 화면이 최대 10건을 보여 주므로 모든 평가 지표도 Top 10을 기준으로 한다.
    private static final int RESULT_LIMIT = 10;

	// 인스턴스를 생성하지 못하도록 생성자를 private으로 설정한다.
    private NewsRankingMetrics() {
    }

    static MetricRow evaluate(String datasetId,
                              NewsStrategyRun run,
                              Map<String, NewsJudgeLabel> labelsByArticleId) {
        // DCG는 현재 전략이 만든 순서의 점수이고, idealDcg는 같은 라벨로 만들 수 있는 최선의 순서다.
        // 둘의 비율인 nDCG는 0~1 범위이며 1에 가까울수록 중요한 기사가 위에 있다.
        double dcg = discountedCumulativeGain(run.items(), labelsByArticleId); // 현재 전략이 만든 순서의 점수
        double idealDcg = idealDiscountedCumulativeGain(labelsByArticleId.values().stream().toList()); // 이상적인 순서
        double ndcg = idealDcg == 0.0 ? 0.0 : dcg / idealDcg; // NDCG = dcg / idealDcg

        // eventId가 같으면 언론사나 제목이 달라도 같은 구체적 사건을 다루는 중복 기사로 본다.
        Set<String> uniqueEvents = new HashSet<>();
        // relevance 2인 투자 유용 사건 중 서로 다른 사건만 별도로 센다.
		// 즉 투자와 관련된 사건들중, 서로 다른 event를 다루는 사건들의 수만 센다.
        Set<String> relevantUniqueEvents = new HashSet<>();
        for (NewsItem item : run.items()) {
            NewsJudgeLabel label = requiredLabel(item, labelsByArticleId);
            uniqueEvents.add(label.eventId()); // 이때 중복된 이벤트는 set이기 때문에 제거된다.
            if (label.relevance() >= 2) {
                relevantUniqueEvents.add(label.eventId()); // 이 중 투자와 관련된 사건들인 경우에는 별도로 relevantUniqueEvents에 정리한다.
            }
        }
        // 예: 10개 기사에 고유 eventId가 7개면 (10-7)/10 = 0.3, 즉 중복률 30%다.
        double redundancyRate = run.items().isEmpty()
                ? 0.0
                : (run.items().size() - uniqueEvents.size()) / (double) run.items().size();
        // 최대 10개 중 실제 선택한 비율이다. 8개를 반환했다면 yieldAt10은 0.8이다.
        // 이는 관련성 품질이 아니라 중복 제거 후 결과를 충분히 채웠는지를 나타낸다.
        double yieldAt10 = run.items().size() / (double) RESULT_LIMIT;

        return new MetricRow(
                datasetId,
                run.strategy(),
                run.similarityThreshold(),
                ndcg,
                redundancyRate,
                relevantUniqueEvents.size(), // 투자와 관련된 기사들 중 고유한 기사들의 수를 나타낸다.
                yieldAt10,
                run.items().size(),
                run.elapsedMillis());
    }

	// 실제 전략이 뽑은 순서의 점수(DCG)를 계산한다.
    private static double discountedCumulativeGain(
            List<NewsItem> items,
            Map<String, NewsJudgeLabel> labelsByArticleId) {
        double score = 0.0;
        for (int index = 0; index < items.size(); index++) {
            int relevance = requiredLabel(items.get(index), labelsByArticleId).relevance();
            // 같은 relevance라도 아래 순위일수록 log2 할인값으로 기여도가 작아진다.
            // index는 0부터 시작하므로 첫 항목의 분모가 log2(2)=1이 되도록 2를 더한다.
			// 즉 relevance가 2인 기사들이 상위권에 위치할 수록 점수가 높아진다.
            score += gain(relevance) / log2(index + 2.0);
        }
        return score;
    }

	// Judge 라벨 기준으로 만들 수 있는 최적의 순서 점수(Ideal DCG)를 계산한다.
    private static double idealDiscountedCumulativeGain(List<NewsJudgeLabel> labels) {
        // Judge 라벨을 relevance 내림차순으로 정렬한 가상의 최선 Top 10을 만든다.
        // 실제 DCG를 이 값으로 나누면 데이터셋마다 기사 난이도가 달라도 비교 가능한 nDCG가 된다.

		// Relevance score를 기반으로 relevence의 내림차순으로 정렬한 다음 score를 계산한다. 이 경우 relevance score가 높을수록 상위권이므로 가장 이상적인 DCG가 된다.
        List<Integer> idealRelevances = labels.stream()
                .map(NewsJudgeLabel::relevance)
                .sorted(Comparator.reverseOrder())
                .limit(RESULT_LIMIT)
                .toList();
        double score = 0.0;
        for (int index = 0; index < idealRelevances.size(); index++) {
            score += gain(idealRelevances.get(index)) / log2(index + 2.0);
        }
        return score;
    }

    private static NewsJudgeLabel requiredLabel(
            NewsItem item,
            Map<String, NewsJudgeLabel> labelsByArticleId) {
        // 전략 결과의 기사 링크에서 수집/판정 때와 똑같은 articleId를 다시 만든다.
        String articleId = EvaluationArticleIds.from(item);
        NewsJudgeLabel label = labelsByArticleId.get(articleId);
        if (label == null) {
            throw new IllegalStateException("평가 라벨이 없는 기사입니다: " + articleId);
        }
        return label;
    }

    private static double gain(int relevance) {
        // relevance 0,1,2를 각각 gain 0,1,3으로 바꿔 높은 관련도에 더 큰 보상을 준다.
        return Math.pow(2.0, relevance) - 1.0;
    }

    private static double log2(double value) {
        // Java 표준 Math에는 log2가 없으므로 밑변환 공식 log(x)/log(2)를 사용한다.
        return Math.log(value) / Math.log(2.0);
    }

    /**
     * CSV 한 행에 기록할 전략별 평가 결과다.
     */
    record MetricRow(
            String datasetId, // 어느 고정 후보 데이터셋의 결과인지 식별한다.
            NewsRankingType strategy, // 평가한 랭킹 전략 종류
            Double similarityThreshold, // 해당 실행의 중복 제거 임계값; KEYWORD는 null
            double ndcgAt10, // 관련도가 높은 기사를 위에 배치한 정도(NDCG@10)
            double redundancyRateAt10, // 선택 기사 중 같은 사건이 반복된 비율
            int relevantUniqueEventsAt10, // relevance 2인 서로 다른 사건 수(즉 투자와 관련된 서로다른 기사 수)
            double yieldAt10, // 최대 10건 대비 실제 선택한 기사 비율
            int selectedCount, // 실제 선택 기사 수
            long elapsedMillis // rank(...)에 걸린 시간
    ) {
    }
}
