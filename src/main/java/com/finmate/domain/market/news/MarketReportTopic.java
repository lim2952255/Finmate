package com.finmate.domain.market.news;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

// 뉴스 검색을 위한 각각의 주제와 주제별로 필요한 정보, 키워드들을 저장
@Getter
@RequiredArgsConstructor
public enum MarketReportTopic {
    KOSPI(
            "kospi",
            "KOSPI",
            "K",
            "국내 대표 지수",
            "유가증권시장의 수급과 지수 흐름을 살펴봅니다.",
            "코스피 시장정보",
            List.of(
                    "지수", "증시", "외국인", "기관", "개인", "순매수", "순매도",
                    "상승", "하락", "급등", "급락", "시가총액", "거래대금", "마감", "전망"
            )
    ),
    KOSDAQ(
            "kosdaq",
            "KOSDAQ",
            "KQ",
            "국내 성장주 지수",
            "코스닥시장의 수급과 성장주 흐름을 살펴봅니다.",
            "코스닥 시장정보",
            List.of(
                    "지수", "증시", "외국인", "기관", "개인", "순매수", "순매도",
                    "상승", "하락", "급등", "급락", "바이오", "기술주", "거래대금", "마감", "전망"
            )
    ),
    NASDAQ(
            "nasdaq",
            "NASDAQ",
            "N",
            "미국 기술주 지수",
            "나스닥과 미국 기술주의 주요 움직임을 살펴봅니다.",
            "나스닥 시장정보",
            List.of(
                    "지수", "증시", "연준", "FOMC", "기술주", "빅테크", "반도체",
                    "상승", "하락", "급등", "급락", "마감", "전망", "금리", "인플레이션"
            )
    ),
    SP500(
            "sp500",
            "S&P 500",
            "S&P",
            "미국 대형주 지수",
            "미국 대표 대형주의 흐름과 월가의 시장 전망을 살펴봅니다.",
            "S&P 500 시장정보",
            List.of(
                    "지수", "뉴욕증시", "월가", "연준", "FOMC", "대형주", "상승", "하락",
                    "급등", "급락", "마감", "전망", "금리", "인플레이션", "경기"
            )
    ),
    INTEREST_RATE(
            "interest-rate",
            "금리",
            "%",
            "통화정책과 채권시장",
            "한국은행과 연준의 정책 방향 및 시장금리 변화를 살펴봅니다.",
            "금리 시장정보",
            List.of(
                    "기준금리", "금리", "금통위", "한국은행", "연준", "FOMC", "인상", "인하",
                    "동결", "채권", "국채", "물가", "인플레이션", "통화정책", "전망"
            )
    ),
    EXCHANGE_RATE(
            "exchange-rate",
            "환율",
            "FX",
            "외환시장",
            "원·달러를 중심으로 주요 통화와 외환시장 흐름을 살펴봅니다.",
            "환율 시장정보",
            List.of(
                    "환율", "원달러", "달러", "원화", "엔화", "유로", "강세", "약세",
                    "상승", "하락", "외환", "환시", "금리", "무역", "수출", "수입"
            )
    );

    private final String code;
    private final String displayName;
    private final String symbol;
    private final String category;
    private final String description;
    private final String query;
    private final List<String> titleKeywords;

    public static MarketReportTopic fromCode(String code) {
        return Arrays.stream(values())
                .filter(topic -> topic.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장 리포트 주제입니다: " + code));
    }
}
