package com.finmate.service.learning;

import com.finmate.domain.learning.InvestmentLearningCategory;
import com.finmate.domain.learning.InvestmentLearningConceptSummary;
import com.finmate.domain.stock.concept.StockConceptCode;
import org.springframework.stereotype.Component;

import java.util.List;

// 지원하는 투자 학습 코드
@Component
public class InvestmentLearningCatalog {
    private final List<InvestmentLearningConceptSummary> concepts = List.of(
            concept(StockConceptCode.CASH_MARGIN_RECEIVABLE_RELATIONSHIP, InvestmentLearningCategory.ACCOUNT_AND_ORDER,
                    "예수금·증거금·미수금 연결", "주문부터 결제일까지 계좌의 돈이 어떤 이름으로 바뀌는지 이해합니다."),
            concept(StockConceptCode.MARGIN_TRADING_AND_FORCED_LIQUIDATION, InvestmentLearningCategory.ACCOUNT_AND_ORDER,
                    "미수거래와 반대매매", "결제대금이 부족할 때 왜 강제 매도가 일어날 수 있는지 살펴봅니다."),
            concept(StockConceptCode.CREDIT_TRADING_VS_MARGIN_TRADING, InvestmentLearningCategory.ACCOUNT_AND_ORDER,
                    "신용거래와 미수거래의 차이", "둘 다 빌린 돈을 쓰지만 기간·이자·담보 관리가 어떻게 다른지 비교합니다."),
            concept(StockConceptCode.HEDGING_VS_SPECULATION, InvestmentLearningCategory.RISK_AND_PORTFOLIO,
                    "헤지와 투기의 차이", "같은 파생상품도 기존 위험을 줄이는지 새 위험을 감수하는지에 따라 목적이 달라집니다."),
            concept(StockConceptCode.DIVERSIFICATION, InvestmentLearningCategory.RISK_AND_PORTFOLIO,
                    "분산투자", "한 자산의 실패가 전체 자산에 미치는 충격을 줄이는 기본 원리를 배웁니다."),
            concept(StockConceptCode.CORRELATION, InvestmentLearningCategory.RISK_AND_PORTFOLIO,
                    "상관관계", "자산들이 같은 방향으로 움직이는 정도와 분산투자의 관계를 이해합니다."),
            concept(StockConceptCode.ASSET_ALLOCATION, InvestmentLearningCategory.RISK_AND_PORTFOLIO,
                    "자산배분", "주식·채권·현금 등 자산군의 비중을 목표와 위험에 맞게 정하는 방법입니다."),
            concept(StockConceptCode.PORTFOLIO_REBALANCING, InvestmentLearningCategory.RISK_AND_PORTFOLIO,
                    "포트폴리오 리밸런싱", "시장 변화로 흐트러진 자산 비중을 목표 비중으로 되돌리는 과정을 배웁니다."),
            concept(StockConceptCode.ETF_VS_FUND, InvestmentLearningCategory.FUND_AND_ETF,
                    "ETF와 펀드의 차이", "둘 다 여러 자산에 투자하지만 거래 방식과 가격 결정이 다릅니다."),
            concept(StockConceptCode.ETF_NAV_AND_PREMIUM_DISCOUNT, InvestmentLearningCategory.FUND_AND_ETF,
                    "ETF의 NAV와 괴리율", "ETF 시장가격이 실제 보유자산 가치와 얼마나 벌어졌는지 읽는 방법입니다."),
            concept(StockConceptCode.LEVERAGED_AND_INVERSE_ETF, InvestmentLearningCategory.FUND_AND_ETF,
                    "레버리지·인버스 ETF", "기초지수의 일간 수익률을 배수 또는 반대 방향으로 추종하는 구조를 이해합니다."),
            concept(StockConceptCode.CURRENCY_HEDGED_VS_UNHEDGED, InvestmentLearningCategory.FUND_AND_ETF,
                    "환헤지와 환노출", "해외자산 수익률에 환율 변동을 포함할지 줄일지 구분합니다."),
            concept(StockConceptCode.SIDECAR, InvestmentLearningCategory.MARKET_SYSTEM,
                    "사이드카", "선물가격 급변 시 프로그램매매의 충격을 잠시 늦추는 장치입니다."),
            concept(StockConceptCode.CIRCUIT_BREAKER, InvestmentLearningCategory.MARKET_SYSTEM,
                    "서킷브레이커", "시장 전체가 급락할 때 거래를 단계적으로 멈추는 안전장치입니다."),
            concept(StockConceptCode.SHORT_SELLING, InvestmentLearningCategory.DERIVATIVES_AND_SHORT_SELLING,
                    "공매도", "주식을 빌려 먼저 판 뒤 나중에 사서 갚는 거래의 수익과 위험을 이해합니다."),
            concept(StockConceptCode.FUTURES, InvestmentLearningCategory.DERIVATIVES_AND_SHORT_SELLING,
                    "선물", "미래 시점의 가격을 지금 정하는 표준화 계약과 증거금 구조를 배웁니다."),
            concept(StockConceptCode.OPTIONS, InvestmentLearningCategory.DERIVATIVES_AND_SHORT_SELLING,
                    "옵션", "미래에 사거나 팔 수 있는 권리인 콜·풋옵션의 구조를 이해합니다.")
    );

    public List<InvestmentLearningConceptSummary> getConcepts() {
        return concepts;
    }

    public boolean supports(StockConceptCode conceptCode) {
        return concepts.stream().anyMatch(concept -> concept.conceptCode() == conceptCode);
    }

    private InvestmentLearningConceptSummary concept(StockConceptCode code,
                                                     InvestmentLearningCategory category,
                                                     String title,
                                                     String summary) {
        return new InvestmentLearningConceptSummary(code, category, title, summary);
    }
}
