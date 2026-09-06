package com.finmate.evaluation.news;

import com.finmate.domain.market.news.MarketReportTopic;
import com.finmate.service.stock.news.StockNewsRankingPolicy;

import java.util.List;

// 평가에 사용할 종목과 주제들, 그리고 검색조건을 설정한 Enum
enum EvaluationSubject {
    // 시장 리포트에서 사용하는 6개 주제다.
    KOSPI(
            "코스피",
            "MARKET",
            MarketReportTopic.KOSPI.getQuery(),
            MarketReportTopic.KOSPI.getTitleKeywords()),
    KOSDAQ(
            "코스닥",
            "MARKET",
            MarketReportTopic.KOSDAQ.getQuery(),
            MarketReportTopic.KOSDAQ.getTitleKeywords()),
    NASDAQ(
            "나스닥",
            "MARKET",
            MarketReportTopic.NASDAQ.getQuery(),
            MarketReportTopic.NASDAQ.getTitleKeywords()),
    SP500(
            "S&P 500",
            "MARKET",
            MarketReportTopic.SP500.getQuery(),
            MarketReportTopic.SP500.getTitleKeywords()),
    INTEREST_RATE(
            "금리",
            "MARKET",
            MarketReportTopic.INTEREST_RATE.getQuery(),
            MarketReportTopic.INTEREST_RATE.getTitleKeywords()),
    EXCHANGE_RATE(
            "환율",
            "MARKET",
            MarketReportTopic.EXCHANGE_RATE.getQuery(),
            MarketReportTopic.EXCHANGE_RATE.getTitleKeywords()),
    // 업종과 기업 특성이 다른 대표 종목 6개다.
    SAMSUNG(
            "삼성전자",
            "STOCK",
            "삼성전자 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords()),
    SK_HYNIX(
            "SK하이닉스",
            "STOCK",
            "SK하이닉스 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords()),
    NAVER(
            "NAVER",
            "STOCK",
            "NAVER 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords()),
    KAKAO(
            "카카오",
            "STOCK",
            "카카오 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords()),
    KB_FINANCIAL(
            "KB금융",
            "STOCK",
            "KB금융 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords()),
    HYUNDAI_MOTOR(
            "현대차",
            "STOCK",
            "현대차 시장정보",
            StockNewsRankingPolicy.importantTitleKeywords());

    private final String displayName; // 로그와 Judge 설명에 사용할 사람이 읽는 이름
    private final String subjectType; // MARKET 또는 STOCK: Judge가 평가 대상 성격을 이해하는 정보
    private final String query; // 네이버 뉴스 API에 실제 전달할 검색어
    private final List<String> keywords; // 운영 랭킹 전략에 전달할 중요 키워드

    EvaluationSubject(String displayName,
                      String subjectType,
                      String query,
                      List<String> keywords) {
        this.displayName = displayName;
        this.subjectType = subjectType;
        this.query = query;
        // enum 바깥에서 목록을 수정해 평가 조건이 실행 중 바뀌지 않도록 불변 복사본을 보관한다.
        this.keywords = List.copyOf(keywords);
    }

    String displayName() {
        return displayName;
    }

    String subjectType() {
        return subjectType;
    }

    String query() {
        return query;
    }

    List<String> keywords() {
        return keywords;
    }

}
