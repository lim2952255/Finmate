package com.finmate.evaluation.news;

import com.finmate.domain.news.dto.NewsItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 한 주제와 한 수집 시각에 고정한 뉴스 후보 집합이다.
 * 평가 실행은 네이버 API를 다시 호출하지 않고 이 레코드의 동일 후보를 네 전략에 전달한다.
 *
 * <p>외부 뉴스 검색 결과는 시간에 따라 달라진다. 먼저 이 값을 JSON 파일로 고정한 뒤 모든 전략에
 * 똑같이 전달해야 전략 차이만 비교할 수 있다. 이 레코드는 그 JSON의 저장 형식이기도 하다.</p>
 */
record NewsEvaluationDataset(
        String datasetId, // 주제 코드와 수집 시각을 합친 실행 간 고유 식별자
        String subjectCode, // KOSPI, SAMSUNG 같은 enum 코드
        String subjectName, // 코스피, 삼성전자처럼 사람이 읽는 이름
        String subjectType, // 시장 주제인지 개별 종목인지 나타내는 MARKET/STOCK
        String query, // 후보를 가져올 때 네이버 API에 전달한 검색어
        OffsetDateTime collectedAt, // 시간대가 포함된 실제 수집 시각
        List<String> keywords, // 모든 전략이 공통으로 사용할 중요 키워드
        List<NewsItem> candidates // 네이버에서 한 번 수집해 고정한 최대 80개 후보
) {
    // record의 compact constructor다. Jackson 역직렬화나 직접 생성 과정에서 null 목록이 들어와도
    // 평가 코드가 매번 null을 검사하지 않도록 빈 불변 목록으로 정규화한다.
    NewsEvaluationDataset {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
