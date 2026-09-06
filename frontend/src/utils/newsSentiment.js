// 백엔드 NewsSentiment enum과 화면에 표시할 한국어 용어를 한곳에서 관리한다.
export const NEWS_SENTIMENT_LABELS = {
  POSITIVE: "호재",
  NEUTRAL: "보통",
  NEGATIVE: "악재"
};

// 감성값이 없는 이전 캐시는 보통으로 계산해 기사 수와 요약 집계의 합계를 일치시킨다.
export function normalizeNewsSentiment(sentiment) {
  return NEWS_SENTIMENT_LABELS[sentiment] ? sentiment : "NEUTRAL";
}

export function countNewsSentiments(items) {
  return items.reduce((counts, item) => {
    counts[normalizeNewsSentiment(item.sentiment)] += 1;
    return counts;
  }, { POSITIVE: 0, NEUTRAL: 0, NEGATIVE: 0 });
}
