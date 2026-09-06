package com.finmate.evaluation.news;

/**
 * LLM judge가 기사 하나에 부여한 투자 관련도와 동일 사건 그룹이다.
 * 이 값은 뉴스 감성(POSITIVE/NEGATIVE)이 아니라 랭킹 품질을 계산하기 위한 평가 라벨이다.
 */
record NewsJudgeLabel(
        String articleId, // EvaluationArticleIds가 만든 기사 식별자
        int relevance, // 투자 유용성: 0=무관, 1=제한적 관련, 2=직접 유용
        String eventId, // 같은 구체적 사건을 다루는 기사들이 공유하는 그룹 ID
        String reason // Judge가 해당 관련도와 사건 그룹을 선택한 짧은 근거
) {
}
