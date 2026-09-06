package com.finmate.evaluation.news;

import com.finmate.domain.news.dto.NewsItem;

import java.io.IOException;
import java.util.List;

/**
 * 평가 기사에 관련도와 사건 그룹을 부여하는 judge의 공통 규약이다.
 * 즉 LLM Judge가 반드시 따라야 하는 규약들을 정의한다.
 */
interface NewsJudge {

    // 한 데이터셋에서 전략들이 선택한 기사 합집합을 LLM Judge가 판단하여 라벨링을 하고, 결과 레포트를 생성한다.
    NewsJudgeReport judge(NewsEvaluationDataset dataset, List<NewsItem> articles)
            throws IOException, InterruptedException;

    // 이전에 저장한 보고서가 현재 모델·프롬프트·기사 집합과 같은 조건인지 확인한다.
    // true면 API 비용을 들여 다시 판정하지 않고 기존 라벨 파일을 재사용할 수 있다.
    boolean isCompatible(NewsJudgeReport report, List<NewsItem> articles);
}
