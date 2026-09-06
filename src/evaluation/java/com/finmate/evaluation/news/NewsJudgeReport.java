package com.finmate.evaluation.news;

import java.util.List;

/**
 * 재현성을 위해 judge 모델과 프롬프트 버전을 라벨 결과와 함께 저장한다.
 * JSON 파일로 저장되며 조건과 기사 집합이 같으면 다음 평가에서 OpenAI API를 다시 호출하지 않고 재사용한다.
 */
record NewsJudgeReport(
        String datasetId, // 어떤 고정 데이터셋을 판정했는지 나타낸다.
        String judgeModel, // LLM judge에 사용한 고정 OpenAI 모델 ID
        String promptVersion, // 평가 기준이 바뀌었는지 확인하는 프롬프트 버전
        List<NewsJudgeLabel> articles // Judge가 반환한 기사별 평가결과
) {
    // 라벨 목록이 null이면 빈 목록으로 바꾸고 외부 수정이 불가능한 복사본을 보관한다.
    NewsJudgeReport {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
