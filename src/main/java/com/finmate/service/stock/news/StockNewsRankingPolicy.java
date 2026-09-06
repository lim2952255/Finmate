package com.finmate.service.stock.news;

import java.util.List;

/**
 * 운영 종목 뉴스와 오프라인 평가가 공유하는 투자 관련 제목 키워드 정책이다.
 * 한쪽만 변경되어 비교 조건이 달라지는 것을 방지하기 위해 불변 목록으로 제공한다.
 */
public final class StockNewsRankingPolicy {
    private static final List<String> IMPORTANT_TITLE_KEYWORDS = List.of(
            "실적", "매출", "영업이익", "순이익",
            "주가", "투자", "애널리스트", "컨센서스",
            "외국인", "기관", "순매수", "순매도",
            "수주", "계약", "증설", "배당", "자사주",
            "상승", "하락", "급등", "급락");

    private StockNewsRankingPolicy() {
    }

    public static List<String> importantTitleKeywords() {
        return IMPORTANT_TITLE_KEYWORDS;
    }
}
