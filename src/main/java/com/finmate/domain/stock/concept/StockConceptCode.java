package com.finmate.domain.stock.concept;

public enum StockConceptCode {
    DAILY_CANDLE_CHART, // 일봉 차트 보는 법
    PER, // PER 개념 카드
    PBR, // PBR 개념 카드
    EPS, // EPS 개념 카드
    BPS, // BPS 개념 카드
    MARKET_CAP, // 시가총액 개념 카드
    LISTED_SHARES, // 상장 주식수 개념 카드
    FOREIGN_HOLDING_QUANTITY, // 외국인 보유수량 개념 카드
    FOREIGN_EXHAUSTION_RATE, // 외국인 소진율 개념 카드
    VALUATION_AND_PROFITABILITY, // 수익성과 기업가치(PER·PBR·ROE·EPS·BPS의 관계를 통합해서 설명) 개념 카드
    FINANCIAL_ANALYSIS_METRICS, // 재무 분석 기준(YoY,QoQ,TTM,런레이트) 개념 카드
    REVENUE, // 매출액 분석 개념 카드
    OPERATING_PROFIT, // 영업이익 분석 개념 카드
    NET_INCOME, // 당기순이익 분석 개념 카드
    FINANCIAL_RATIOS, // 재무비율 분석 개념 카드
    INCOME_STATEMENT, // 손익계산서 분석 개념 카드
    BALANCE_SHEET, // 대차대조표 분석 개념 카드
    INVESTOR_TRADING_FLOW, // 투자자별 매매동향 분석 개념 카드
    SHORT_SELLING_AND_SECURITIES_LENDING, // 공매도 거래와 대차잔고 통합 개념 카드

    CASH_MARGIN_RECEIVABLE_RELATIONSHIP, // 예수금·증거금·미수금 연결
    MARGIN_TRADING_AND_FORCED_LIQUIDATION, // 미수거래와 반대매매
    CREDIT_TRADING_VS_MARGIN_TRADING, // 신용거래와 미수거래의 차이
    HEDGING_VS_SPECULATION, // 헤지와 투기의 차이
    DIVERSIFICATION, // 분산투자
    CORRELATION, // 상관관계
    ASSET_ALLOCATION, // 자산배분
    PORTFOLIO_REBALANCING, // 포트폴리오 리밸런싱
    ETF_VS_FUND, // ETF와 펀드의 차이
    ETF_NAV_AND_PREMIUM_DISCOUNT, // ETF의 NAV와 괴리율
    LEVERAGED_AND_INVERSE_ETF, // 레버리지·인버스 ETF
    CURRENCY_HEDGED_VS_UNHEDGED, // 환헤지와 환노출
    SIDECAR, // 사이드카
    CIRCUIT_BREAKER, // 서킷브레이커
    SHORT_SELLING, // 공매도
    FUTURES, // 선물
    OPTIONS // 옵션
}
