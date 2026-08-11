package com.finmate.domain.learning;

// 투자학습 카테고리
public enum InvestmentLearningCategory {
    ACCOUNT_AND_ORDER("계좌·거래", "주문 결제와 빌린 자금의 흐름을 이해합니다."),
    RISK_AND_PORTFOLIO("위험관리·포트폴리오", "위험을 나누고 목표 비중을 유지하는 방법을 배웁니다."),
    FUND_AND_ETF("펀드·ETF", "간접투자 상품의 구조와 환율 영향을 구분합니다."),
    MARKET_SYSTEM("시장 안전장치", "급격한 시장 변동을 완화하는 제도를 살펴봅니다."),
    DERIVATIVES_AND_SHORT_SELLING("파생상품·공매도", "가격 하락과 미래 가격을 활용하는 거래 구조를 배웁니다.");

    private final String displayName;
    private final String description;

    InvestmentLearningCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
