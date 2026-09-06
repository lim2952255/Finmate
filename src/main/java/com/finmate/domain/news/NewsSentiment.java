package com.finmate.domain.news;

/**
 * 종목 뉴스와 시장 리포트에 표시할 세 단계 감성 결과다.
 * KR-FinBert-SC의 positive/neutral/negative 라벨을 화면 용어인 호재/보통/악재로 대응한다.
 */
public enum NewsSentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}
