package com.finmate.service.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.concept.StockConceptVisualResponse;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class StockConceptVisualCatalog {
    private static final String ASSET_ROOT = "/images/stock-concepts/";

    private final Map<StockConceptCode, StockConceptVisualResponse> visuals = createVisuals();

    // 시각화자료를 조회한다.
    public StockConceptVisualResponse getVisual(StockConceptCode conceptCode) {
        StockConceptVisualResponse visual = visuals.get(conceptCode);
        if (visual == null) {
            throw new IllegalArgumentException("개념 그림을 찾을 수 없습니다: " + conceptCode);
        }
        return visual;
    }

    // 시각화자료 등록
    private Map<StockConceptCode, StockConceptVisualResponse> createVisuals() {
        Map<StockConceptCode, StockConceptVisualResponse> catalog = new EnumMap<>(StockConceptCode.class);

        register(catalog, "daily-candle-chart.svg",
                "빨간 양봉과 파란 음봉에 시가, 고가, 저가, 종가 위치를 표시하고 시가와 종가가 단일가매매로 결정되는 흐름",
                "몸통은 시가와 종가 사이, 위아래 꼬리는 하루의 고가와 저가를 보여줍니다.",
                StockConceptCode.DAILY_CANDLE_CHART);
        register(catalog, "earnings-valuation.svg",
                "회사의 당기순이익을 주식 수로 나누어 EPS를 구하고 주가를 EPS로 나누어 PER을 구하는 흐름",
                "회사가 번 이익을 한 주 기준으로 바꾸면 EPS, 현재 주가와 비교하면 PER이 됩니다.",
                StockConceptCode.PER, StockConceptCode.EPS);
        register(catalog, "asset-valuation.svg",
                "회사 자산에서 부채를 빼 자기자본을 구하고 주식 수로 나누어 BPS와 PBR을 구하는 흐름",
                "자산에서 부채를 뺀 자기자본이 BPS와 PBR 계산의 출발점입니다.",
                StockConceptCode.PBR, StockConceptCode.BPS);
        register(catalog, "market-cap.svg",
                "한 주의 가격에 상장주식 수를 곱해 회사 전체 시장가치인 시가총액을 계산하는 그림",
                "주가가 한 조각의 가격이라면 시가총액은 모든 주식 조각을 합친 회사의 시장가격입니다.",
                StockConceptCode.MARKET_CAP, StockConceptCode.LISTED_SHARES);
        register(catalog, "foreign-ownership.svg",
                "외국인 보유수량을 외국인 보유 가능 수량과 비교해 외국인 소진율을 계산하는 그림",
                "외국인 보유수량은 실제 보유량이고, 소진율은 보유 가능한 한도 중 사용한 비율입니다.",
                StockConceptCode.FOREIGN_HOLDING_QUANTITY, StockConceptCode.FOREIGN_EXHAUSTION_RATE);
        register(catalog, "valuation-profitability.svg",
                "시가총액과 당기순이익, 자기자본을 PER과 PBR, ROE로 연결한 삼각 관계도",
                "PER·PBR·ROE는 각각 가격, 이익, 자기자본을 서로 비교한 지표입니다.",
                StockConceptCode.VALUATION_AND_PROFITABILITY);
        register(catalog, "financial-analysis-metrics.svg",
                "YoY와 QoQ, TTM과 런레이트를 계산하고 실제 실적을 컨센서스와 비교하는 흐름",
                "과거와의 성장 비교, 최근 실적의 연환산, 시장 기대와의 차이를 구분해서 봅니다.",
                StockConceptCode.FINANCIAL_ANALYSIS_METRICS);
        register(catalog, "income-statement.svg",
                "빵집 매출에서 영업비용과 영업외손익, 세금을 차례로 반영해 당기순이익에 이르는 손익계산 흐름",
                "매출에서 사업에 든 비용과 영업외손익, 세금을 차례로 반영하면 당기순이익이 남습니다.",
                StockConceptCode.REVENUE, StockConceptCode.OPERATING_PROFIT,
                StockConceptCode.NET_INCOME, StockConceptCode.INCOME_STATEMENT);
        register(catalog, "financial-ratios.svg",
                "성장성, 수익성, 재무 안정성을 서로 다른 재무비율로 확인하는 그림",
                "재무비율은 성장성·수익성·안정성을 서로 다른 각도에서 읽는 도구입니다.",
                StockConceptCode.FINANCIAL_RATIOS);
        register(catalog, "balance-sheet.svg",
                "회사의 자산이 부채와 자기자본으로 조달된다는 재무상태표의 균형 구조",
                "회사가 가진 자산은 채권자의 몫인 부채와 주주의 몫인 자기자본으로 구성됩니다.",
                StockConceptCode.BALANCE_SHEET);
        register(catalog, "investor-flow.svg",
                "외국인과 개인, 기관별 매수수량에서 매도수량을 빼 순매수를 계산하는 그림",
                "투자자별 수급은 보유 비율이 아니라 일정 기간 매수와 매도가 어느 쪽으로 기울었는지를 보여줍니다.",
                StockConceptCode.INVESTOR_TRADING_FLOW);
        register(catalog, "short-selling-loan.svg",
                "주식을 빌리는 대차거래와 빌린 주식을 시장에 파는 공매도, 이후 되사서 반환하는 흐름",
                "대차잔고는 빌려 간 주식의 잔량이고 공매도 거래량은 실제로 매도된 수량이므로 같은 값이 아닙니다.",
                StockConceptCode.SHORT_SELLING_AND_SECURITIES_LENDING);

        register(catalog, "learning-account-trading.svg",
                "예수금이 주문 증거금으로 묶이고 결제대금이 부족하면 미수금과 반대매매 위험으로 이어지는 흐름",
                "주문 가능 금액과 실제 결제 가능한 현금은 같지 않을 수 있습니다.",
                StockConceptCode.CASH_MARGIN_RECEIVABLE_RELATIONSHIP,
                StockConceptCode.MARGIN_TRADING_AND_FORCED_LIQUIDATION,
                StockConceptCode.CREDIT_TRADING_VS_MARGIN_TRADING);
        register(catalog, "learning-risk-portfolio.svg",
                "서로 다르게 움직이는 자산을 배분하고 목표 비중에서 벗어나면 리밸런싱하는 포트폴리오 흐름",
                "분산과 자산배분은 위험을 없애는 것이 아니라 한 위험에 집중되지 않게 조절하는 방법입니다.",
                StockConceptCode.HEDGING_VS_SPECULATION,
                StockConceptCode.DIVERSIFICATION,
                StockConceptCode.CORRELATION,
                StockConceptCode.ASSET_ALLOCATION,
                StockConceptCode.PORTFOLIO_REBALANCING);
        register(catalog, "learning-etf-fund.svg",
                "여러 자산을 담은 펀드와 ETF, ETF의 순자산가치와 시장가격 및 환율 영향을 비교하는 그림",
                "ETF는 장중 시장가격으로 거래되며 NAV, 괴리율, 환율과 일간 추종 구조를 함께 확인해야 합니다.",
                StockConceptCode.ETF_VS_FUND,
                StockConceptCode.ETF_NAV_AND_PREMIUM_DISCOUNT,
                StockConceptCode.LEVERAGED_AND_INVERSE_ETF,
                StockConceptCode.CURRENCY_HEDGED_VS_UNHEDGED);
        register(catalog, "learning-market-safety.svg",
                "시장 충격이 커질 때 프로그램매매를 잠시 제한하는 사이드카와 시장 전체 거래를 멈추는 서킷브레이커",
                "사이드카와 서킷브레이커는 적용 대상과 발동 효과가 다른 시장 안전장치입니다.",
                StockConceptCode.SIDECAR,
                StockConceptCode.CIRCUIT_BREAKER);
        register(catalog, "learning-derivatives.svg",
                "주식을 빌려 파는 공매도와 미래 가격을 계약하는 선물, 매매 권리를 거래하는 옵션의 구조",
                "공매도·선물·옵션은 하락 대응과 헤지에 쓰일 수 있지만 손실 구조와 의무가 서로 다릅니다.",
                StockConceptCode.SHORT_SELLING,
                StockConceptCode.FUTURES,
                StockConceptCode.OPTIONS);

        return Map.copyOf(catalog);
    }

    // 개념 코드와 시각화자료를 매핑해서 연결한다.
    private void register(Map<StockConceptCode, StockConceptVisualResponse> catalog,
                          String fileName,
                          String altText,
                          String caption,
                          StockConceptCode... conceptCodes) {
        StockConceptVisualResponse visual = new StockConceptVisualResponse(
                ASSET_ROOT + fileName,
                altText,
                caption
        );
        for (StockConceptCode conceptCode : conceptCodes) {
            catalog.put(conceptCode, visual);
        }
    }
}
