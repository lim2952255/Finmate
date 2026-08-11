package com.finmate.service.stock.concept;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.concept.StockConceptAnalysisResponse;
import com.finmate.domain.stock.dto.concept.StockConceptAnalysisResponse.Metric;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.BalanceSheet;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.FinancialAnalysis;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.FinancialMetric;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.FinancialRatio;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.IncomeStatement;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.InvestorTrade;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.LoanTransaction;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.ShortSale;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo.Quote;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.global.format.DisplayFormatUtils;
import com.finmate.repository.stock.StockRepository;
import com.finmate.service.stock.DomesticStockDetailQueryService;
import com.finmate.service.stock.DomesticStockDetailRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// 개념카드 코드에 따라서 적절한 종목의 데이터를 조회해서 개념카드 DTO를 생성하는 서비스
@Service
@RequiredArgsConstructor
public class StockConceptAnalysisService {
    private static final BigDecimal ONE_HUNDRED_MILLION = BigDecimal.valueOf(100_000_000L);
    private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final StockRepository stockRepository; //
    private final DomesticStockDetailRefreshService detailRefreshService; // 종목 상세정보 갱신
    private final DomesticStockDetailQueryService detailQueryService; // 종목 상세정보 조회

    // 종목의 실제 데이터를 조회해서 개념카드 DTO를 생성해서 리턴하는 메서드
    public StockConceptAnalysisResponse analyze(Long stockId, StockConceptCode conceptCode) {
        // 일봉 읽기처럼 종목별 수치가 필요 없는 학습 카드는 KIS·DB 상세 조회를 수행하지 않는다.
        if (conceptCode == StockConceptCode.DAILY_CANDLE_CHART || isInvestmentLearningConcept(conceptCode)) {
            return null;
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다: " + stockId));

        // 카드에 표시할 개념카드 제목 생성
        String heading = stock.getNameKo() + " 실제 데이터로 보기";
        if (!isDomestic(stock)) {
            return StockConceptAnalysisResponse.unavailable(
                    heading, "현재 종목 맞춤 개념 설명은 국내주식 데이터만 지원합니다.");
        }

        // 종목 상세정보 갱신이 필요하면 갱신하고, 종목 상세정보를 Redis에서 조회한다.
        DomesticStockCurrentQuoteSnapshot currentQuote = detailRefreshService.refreshIfNeeded(stock);
        // 조회한 종목 상세정보를 기반으로 화면에 표시할 종목 상세정보를 DTO에 담는다.
        DomesticStockDetailInfo detail = detailQueryService.getDetailInfo(stock, currentQuote);

        return switch (conceptCode) {
            case DAILY_CANDLE_CHART -> null; // 위에서 종목 데이터 조회 없이 반환한다.
            case PER -> per(stock, detail); // PER 개념카드 처리
            case PBR -> pbr(stock, detail); // PBR 개념카드 처리
            case EPS -> eps(stock, detail); // EPS 개념카드 처리
            case BPS -> bps(stock, detail); // BPS 개념카드 처리
            case MARKET_CAP -> marketCap(stock, detail); // 시가총액 개념카드 처리
            case LISTED_SHARES -> listedShares(stock, detail); // 상장주식 수 개념카드 처리
            case FOREIGN_HOLDING_QUANTITY -> foreignHolding(stock, detail); // 외국인 보유수량 개념카드 처리
            case FOREIGN_EXHAUSTION_RATE -> foreignExhaustion(stock, detail); // 외국인 소진율 개념카드 처리
            case VALUATION_AND_PROFITABILITY -> valuationAndProfitability(stock, detail); // 수익성과 기업가치 개념카드 처리
            case FINANCIAL_ANALYSIS_METRICS -> financialAnalysis(stock, detail); // 재무 분석기준 개념카드 처리
            case REVENUE -> financialMetric(stock, detail, MetricType.REVENUE); // 매출액 분석 개념카드 처리
            case OPERATING_PROFIT -> financialMetric(stock, detail, MetricType.OPERATING_PROFIT); // 영업이익 분석 개념카드 처리
            case NET_INCOME -> financialMetric(stock, detail, MetricType.NET_INCOME); // 당기순이익 분석 개념카드 처리
            case FINANCIAL_RATIOS -> financialRatios(stock, detail); // 재무비율 분석 개념카드 처리
            case INCOME_STATEMENT -> incomeStatement(stock, detail); // 손익계산서 분석 개념카드 처리
            case BALANCE_SHEET -> balanceSheet(stock, detail); // 대차대조표 분석 개념카드 처리
            case INVESTOR_TRADING_FLOW -> investorFlow(stock, detail); // 투자자별 매매동향 분석 개념카드 처리
            case SHORT_SELLING_AND_SECURITIES_LENDING -> shortSellingAndLending(stock, detail);
            case CASH_MARGIN_RECEIVABLE_RELATIONSHIP, MARGIN_TRADING_AND_FORCED_LIQUIDATION,
                 CREDIT_TRADING_VS_MARGIN_TRADING, HEDGING_VS_SPECULATION, DIVERSIFICATION,
                 CORRELATION, ASSET_ALLOCATION, PORTFOLIO_REBALANCING, ETF_VS_FUND,
                 ETF_NAV_AND_PREMIUM_DISCOUNT, LEVERAGED_AND_INVERSE_ETF,
                 CURRENCY_HEDGED_VS_UNHEDGED, SIDECAR, CIRCUIT_BREAKER, SHORT_SELLING,
                 FUTURES, OPTIONS -> null;
        };
    }

    private boolean isInvestmentLearningConcept(StockConceptCode conceptCode) {
        return switch (conceptCode) {
            case CASH_MARGIN_RECEIVABLE_RELATIONSHIP, MARGIN_TRADING_AND_FORCED_LIQUIDATION,
                 CREDIT_TRADING_VS_MARGIN_TRADING, HEDGING_VS_SPECULATION, DIVERSIFICATION,
                 CORRELATION, ASSET_ALLOCATION, PORTFOLIO_REBALANCING, ETF_VS_FUND,
                 ETF_NAV_AND_PREMIUM_DISCOUNT, LEVERAGED_AND_INVERSE_ETF,
                 CURRENCY_HEDGED_VS_UNHEDGED, SIDECAR, CIRCUIT_BREAKER, SHORT_SELLING,
                 FUTURES, OPTIONS -> true;
            default -> false;
        };
    }

    private StockConceptAnalysisResponse per(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.per() == null) {
            return unavailable(stock, "현재 저장된 PER 데이터가 없습니다.");
        }
        // 공식 정보 등록
        String formula = quote.currentPrice() != null && positive(quote.eps())
                ? "%s ÷ %s = 약 %s".formatted(
                won(quote.currentPrice()), won(quote.eps()), multiple(quote.currentPrice().divide(
                        quote.eps(), 4, RoundingMode.HALF_UP)))
                : "PER = 현재 주가 ÷ EPS";
        // PER 개념 정보
        String interpretation = positive(quote.eps())
                ? "%s의 KIS 제공 PER은 %s입니다. 현재 주가가 KIS 산정 EPS의 약 %s 수준이라는 뜻이며, 이 수치만으로 저평가나 고평가를 판단할 수는 없습니다."
                .formatted(stock.getNameKo(), multiple(quote.per()), multiple(quote.per()))
                : "%s의 EPS가 0 이하이므로 일반적인 PER 비교는 의미가 약합니다. 적자의 원인과 지속 여부를 함께 확인해야 합니다."
                .formatted(stock.getNameKo());
        return available(stock, metrics(
                        metric("현재가", won(quote.currentPrice())),
                        metric("EPS", won(quote.eps())),
                        metric("KIS PER", multiple(quote.per()))),
                formula, interpretation, "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse pbr(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.pbr() == null) {
            return unavailable(stock, "현재 저장된 PBR 데이터가 없습니다.");
        }
        // 공식 정보 등록
        String formula = quote.currentPrice() != null && positive(quote.bps())
                ? "%s ÷ %s = 약 %s".formatted(
                won(quote.currentPrice()), won(quote.bps()), multiple(quote.currentPrice().divide(
                        quote.bps(), 4, RoundingMode.HALF_UP)))
                : "PBR = 현재 주가 ÷ BPS";
        // PBR 개념 정보
        String interpretation = positive(quote.bps())
                ? "%s의 KIS 제공 PBR은 %s입니다. 시장가격이 장부상 주당순자산의 약 %s 수준이라는 뜻입니다. 자산의 실제 가치와 수익성을 함께 확인해야 합니다."
                .formatted(stock.getNameKo(), multiple(quote.pbr()), multiple(quote.pbr()))
                : "%s의 BPS가 0 이하이면 일반적인 PBR 비교가 어렵습니다. 자기자본과 자본잠식 여부를 먼저 확인해야 합니다."
                .formatted(stock.getNameKo());
        return available(stock, metrics(
                        metric("현재가", won(quote.currentPrice())),
                        metric("BPS", won(quote.bps())),
                        metric("KIS PBR", multiple(quote.pbr()))),
                formula, interpretation, "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse eps(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        IncomeStatement latest = first(detail.getIncomeStatements());
        if (quote == null || quote.eps() == null) {
            return unavailable(stock, "현재 저장된 EPS 데이터가 없습니다.");
        }
        String interpretation = quote.eps().signum() > 0
                ? "%s의 KIS 제공 EPS는 %s입니다. KIS가 사용한 이익과 주식 수 기준으로 한 주당 이익이 이 수준이라는 뜻입니다."
                .formatted(stock.getNameKo(), won(quote.eps()))
                : "%s의 KIS 제공 EPS는 %s로 0 이하입니다. 최근 손실이나 일회성 손익이 포함됐는지 확인할 필요가 있습니다."
                .formatted(stock.getNameKo(), won(quote.eps()));
        return available(stock, metrics(
                        metric("KIS EPS", won(quote.eps())),
                        metric("최근 분기 당기순이익", latest == null ? null : amount(latest.netIncome())),
                        metric("상장주식 수", shares(quote.listedShares()))),
                "EPS = 보통주에 귀속되는 이익 ÷ 가중평균 유통주식 수",
                interpretation + " 화면의 최근 분기 순이익과 상장주식 수는 산정 기간과 주식 수 기준이 다를 수 있어 단순 재계산하지 않습니다.",
                latest == null ? "현재 시세 지표" : quarter(latest.fiscalPeriod()) + " 및 현재 시세 지표",
                oldest(detail.getQuoteUpdatedAt(), detail.getFinancialUpdatedAt()));
    }

    private StockConceptAnalysisResponse bps(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        BalanceSheet latest = first(detail.getBalanceSheets());
        if (quote == null || quote.bps() == null) {
            return unavailable(stock, "현재 저장된 BPS 데이터가 없습니다.");
        }
        String interpretation = quote.bps().signum() > 0
                ? "%s의 KIS 제공 BPS는 %s입니다. KIS 산정 기준상 한 주에 대응하는 장부상 순자산을 뜻합니다."
                .formatted(stock.getNameKo(), won(quote.bps()))
                : "%s의 KIS 제공 BPS는 %s입니다. 자기자본이 충분한지와 자본잠식 여부를 먼저 확인해야 합니다."
                .formatted(stock.getNameKo(), won(quote.bps()));
        return available(stock, metrics(
                        metric("KIS BPS", won(quote.bps())),
                        metric("최근 자기자본", latest == null ? null : amount(latest.totalEquity())),
                        metric("상장주식 수", shares(quote.listedShares()))),
                "BPS = 보통주 자기자본 ÷ BPS 산정에 사용한 주식 수",
                interpretation + " 화면의 자기자본과 상장주식 수는 자기주식 등 세부 기준이 다를 수 있어 단순 재계산하지 않습니다.",
                latest == null ? "현재 시세 지표" : quarter(latest.fiscalPeriod()) + " 및 현재 시세 지표",
                oldest(detail.getQuoteUpdatedAt(), detail.getFinancialUpdatedAt()));
    }

    private StockConceptAnalysisResponse marketCap(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.marketCap() == null) {
            return unavailable(stock, "현재 저장된 시가총액 데이터가 없습니다.");
        }
        BigDecimal calculated = quote.currentPrice() == null || quote.listedShares() == null
                ? null
                : quote.currentPrice().multiply(BigDecimal.valueOf(quote.listedShares()))
                .divide(ONE_HUNDRED_MILLION, 2, RoundingMode.HALF_UP);
        String formula = calculated == null
                ? "시가총액 = 주가 × 상장주식 수"
                : "%s × %s = 단순 계산 약 %s".formatted(
                won(quote.currentPrice()), shares(quote.listedShares()), amount(calculated));
        return available(stock, metrics(
                        metric("현재가", won(quote.currentPrice())),
                        metric("상장주식 수", shares(quote.listedShares())),
                        metric("KIS 시가총액", amount(quote.marketCap()))),
                formula,
                "%s의 KIS 제공 시가총액은 %s입니다. 시장에서 형성된 회사 전체의 가격이며 회사가 보유한 현금이나 순자산과 같은 뜻은 아닙니다."
                        .formatted(stock.getNameKo(), amount(quote.marketCap())),
                "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse listedShares(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.listedShares() == null) {
            return unavailable(stock, "현재 저장된 상장주식 수 데이터가 없습니다.");
        }
        return available(stock, metrics(
                        metric("상장주식 수", shares(quote.listedShares())),
                        metric("현재가", won(quote.currentPrice())),
                        metric("KIS 시가총액", amount(quote.marketCap()))),
                "시가총액 = 현재가 × 상장주식 수",
                "%s는 현재 %s가 상장되어 있습니다. 증자·감자·주식분할 등에 따라 주식 수가 바뀌면 주당 지표와 시가총액의 계산도 영향을 받을 수 있습니다."
                        .formatted(stock.getNameKo(), shares(quote.listedShares())),
                "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse foreignHolding(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.foreignHoldingQuantity() == null) {
            return unavailable(stock, "현재 저장된 외국인 보유수량 데이터가 없습니다.");
        }
        BigDecimal holdingRate = ratio(quote.foreignHoldingQuantity(), quote.listedShares());
        return available(stock, metrics(
                        metric("외국인 보유수량", shares(quote.foreignHoldingQuantity())),
                        metric("상장주식 수", shares(quote.listedShares())),
                        metric("단순 보유 비율", percent(holdingRate)),
                        metric("외국인 소진율", percent(quote.foreignExhaustionRate()))),
                holdingRate == null ? null : "%s ÷ %s × 100 = 약 %s".formatted(
                        shares(quote.foreignHoldingQuantity()), shares(quote.listedShares()), percent(holdingRate)),
                "%s의 외국인 보유수량은 %s입니다. 이는 현재 보유량이며, 오늘 외국인이 얼마나 사고팔았는지를 나타내는 순매수와는 다른 값입니다."
                        .formatted(stock.getNameKo(), shares(quote.foreignHoldingQuantity())),
                "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse foreignExhaustion(Stock stock, DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        if (quote == null || quote.foreignExhaustionRate() == null) {
            return unavailable(stock, "현재 저장된 외국인 소진율 데이터가 없습니다.");
        }
        BigDecimal holdingRate = ratio(quote.foreignHoldingQuantity(), quote.listedShares());
        return available(stock, metrics(
                        metric("외국인 소진율", percent(quote.foreignExhaustionRate())),
                        metric("외국인 보유수량", shares(quote.foreignHoldingQuantity())),
                        metric("전체 주식 대비 단순 보유 비율", percent(holdingRate))),
                "외국인 소진율 = 외국인 보유수량 ÷ 외국인 보유 가능 수량 × 100",
                "%s의 외국인 소진율은 %s입니다. 전체 상장주식 중 외국인 보유 비율이 아니라 허용된 외국인 보유 한도 중 사용된 비율입니다."
                        .formatted(stock.getNameKo(), percent(quote.foreignExhaustionRate())),
                "현재 시세 지표", detail.getQuoteUpdatedAt());
    }

    private StockConceptAnalysisResponse valuationAndProfitability(Stock stock,
                                                                    DomesticStockDetailInfo detail) {
        Quote quote = detail.getQuote();
        FinancialRatio latest = first(detail.getFinancialRatios());
        if (quote == null && latest == null) {
            return unavailable(stock, "현재 저장된 가치평가·수익성 데이터가 없습니다.");
        }
        return available(stock, metrics(
                        metric("PER", quote == null ? null : multiple(quote.per())),
                        metric("PBR", quote == null ? null : multiple(quote.pbr())),
                        metric("EPS", quote == null ? null : won(quote.eps())),
                        metric("BPS", quote == null ? null : won(quote.bps())),
                        metric("최근 ROE", latest == null ? null : percent(latest.roe()))),
                "PER = 주가 ÷ EPS · PBR = 주가 ÷ BPS · ROE = 이익 ÷ 자기자본 × 100",
                "%s의 가격 수준은 PER·PBR로, 주당 이익과 순자산은 EPS·BPS로, 자기자본의 이익 창출 효율은 ROE로 확인할 수 있습니다. 서로 연결된 수치지만 산정 기간이 다를 수 있어 하나의 등식처럼 단순 결합하면 안 됩니다."
                        .formatted(stock.getNameKo()),
                latest == null ? "현재 시세 지표" : quarter(latest.fiscalPeriod()) + " 및 현재 시세 지표",
                oldest(detail.getQuoteUpdatedAt(), detail.getFinancialUpdatedAt()));
    }

    private StockConceptAnalysisResponse financialAnalysis(Stock stock, DomesticStockDetailInfo detail) {
        FinancialAnalysis analysis = detail.getFinancialAnalysis();
        if (analysis == null) {
            return unavailable(stock, "최근 분기 실적이 부족해 YoY·QoQ·TTM·런레이트를 계산할 수 없습니다.");
        }
        FinancialMetric revenue = analysis.revenue();
        FinancialMetric operatingProfit = analysis.operatingProfit();
        FinancialMetric netIncome = analysis.netIncome();
        String interpretation = String.join(" ",
                comparisonSentence("매출", revenue),
                comparisonSentence("영업이익", operatingProfit),
                comparisonSentence("당기순이익", netIncome),
                "현재 저장된 데이터에는 애널리스트 컨센서스가 없어 실제 실적과 컨센서스의 차이는 계산하지 않습니다.");
        return available(stock, metrics(
                        metric("최신 분기 매출", amount(revenue.latestQuarter())),
                        metric("매출 YoY", signedPercent(revenue.yoyRate())),
                        metric("매출 QoQ", signedPercent(revenue.qoqRate())),
                        metric("매출 TTM", amount(revenue.ttm())),
                        metric("매출 런레이트", amount(revenue.runRate())),
                        metric("영업이익 TTM", amount(operatingProfit.ttm())),
                        metric("영업이익 런레이트", amount(operatingProfit.runRate())),
                        metric("순이익 TTM", amount(netIncome.ttm())),
                        metric("순이익 런레이트", amount(netIncome.runRate()))),
                revenue.latestQuarter() == null ? null : "매출 런레이트 = %s × 4 = %s".formatted(
                        amount(revenue.latestQuarter()), amount(revenue.runRate())),
                interpretation.trim(), quarter(analysis.latestPeriod()), detail.getFinancialUpdatedAt());
    }

    private StockConceptAnalysisResponse financialMetric(Stock stock,
                                                          DomesticStockDetailInfo detail,
                                                          MetricType type) {
        FinancialAnalysis analysis = detail.getFinancialAnalysis();
        if (analysis == null) {
            return unavailable(stock, "최근 분기 실적이 부족해 해당 항목을 분석할 수 없습니다.");
        }
        FinancialMetric financialMetric = type.extract(analysis);
        return available(stock, metrics(
                        metric("최신 분기", amount(financialMetric.latestQuarter())),
                        metric("YoY", signedPercent(financialMetric.yoyRate())),
                        metric("QoQ", signedPercent(financialMetric.qoqRate())),
                        metric("TTM", amount(financialMetric.ttm())),
                        metric("런레이트", amount(financialMetric.runRate()))),
                financialMetric.latestQuarter() == null ? null : "런레이트 = %s × 4 = %s".formatted(
                        amount(financialMetric.latestQuarter()), amount(financialMetric.runRate())),
                "%s의 %s은(는) 최신 분기 %s입니다. %s 단일 분기의 변화뿐 아니라 TTM과 원래 금액을 함께 확인해야 합니다."
                        .formatted(stock.getNameKo(), type.displayName, amount(financialMetric.latestQuarter()),
                                comparisonSentence(type.displayName, financialMetric)),
                quarter(analysis.latestPeriod()), detail.getFinancialUpdatedAt());
    }

    private StockConceptAnalysisResponse financialRatios(Stock stock, DomesticStockDetailInfo detail) {
        FinancialRatio latest = first(detail.getFinancialRatios());
        if (latest == null) {
            return unavailable(stock, "현재 저장된 재무비율 데이터가 없습니다.");
        }
        return available(stock, metrics(
                        metric("매출 성장률", percent(latest.salesGrowthRate())),
                        metric("영업이익 성장률", percent(latest.operatingProfitGrowthRate())),
                        metric("순이익 성장률", percent(latest.netIncomeGrowthRate())),
                        metric("ROE", percent(latest.roe())),
                        metric("EPS", won(latest.eps())),
                        metric("BPS", won(latest.bps())),
                        metric("유보율", percent(latest.reserveRate())),
                        metric("부채비율", percent(latest.debtRate()))),
                null,
                "%s의 최근 재무비율은 성장성·수익성·안정성을 서로 다른 각도에서 보여줍니다. 비율의 높고 낮음만으로 좋고 나쁨을 정하지 말고 최근 4개 분기의 방향과 업종 특성을 함께 봐야 합니다."
                        .formatted(stock.getNameKo()),
                quarter(latest.fiscalPeriod()), detail.getFinancialUpdatedAt());
    }

    private StockConceptAnalysisResponse incomeStatement(Stock stock, DomesticStockDetailInfo detail) {
        IncomeStatement latest = first(detail.getIncomeStatements());
        if (latest == null) {
            return unavailable(stock, "현재 저장된 손익계산서 데이터가 없습니다.");
        }
        return available(stock, metrics(
                        metric("매출액", amount(latest.revenue())),
                        metric("영업이익", amount(latest.operatingProfit())),
                        metric("경상이익", amount(latest.ordinaryProfit())),
                        metric("당기순이익", amount(latest.netIncome()))),
                "매출액 → 영업비용 반영 → 영업이익 → 영업외손익·세금 반영 → 당기순이익",
                "%s의 %s 실적입니다. 매출에서 최종 이익까지 각 단계의 금액 차이를 보면 본업의 비용과 영업외손익이 결과에 어떻게 반영됐는지 살펴볼 수 있습니다."
                        .formatted(stock.getNameKo(), quarter(latest.fiscalPeriod())),
                quarter(latest.fiscalPeriod()), detail.getFinancialUpdatedAt());
    }

    private StockConceptAnalysisResponse balanceSheet(Stock stock, DomesticStockDetailInfo detail) {
        BalanceSheet latest = first(detail.getBalanceSheets());
        if (latest == null) {
            return unavailable(stock, "현재 저장된 재무상태표 데이터가 없습니다.");
        }
        BigDecimal liabilitiesAndEquity = add(latest.totalLiabilities(), latest.totalEquity());
        return available(stock, metrics(
                        metric("유동자산", amount(latest.currentAssets())),
                        metric("총자산", amount(latest.totalAssets())),
                        metric("유동부채", amount(latest.currentLiabilities())),
                        metric("총부채", amount(latest.totalLiabilities())),
                        metric("자본금", amount(latest.capital())),
                        metric("이익잉여금", amount(latest.retainedEarnings())),
                        metric("자기자본", amount(latest.totalEquity()))),
                liabilitiesAndEquity == null ? "자산 = 부채 + 자기자본" : "총자산 %s · 총부채와 자기자본 합계 %s"
                        .formatted(amount(latest.totalAssets()), amount(liabilitiesAndEquity)),
                "%s의 %s 재무상태입니다. 자산이 어떤 자금으로 마련됐는지와 단기 자산·부채의 구성을 함께 확인할 수 있습니다. 표시 단위의 반올림 때문에 등식에 작은 차이가 날 수 있습니다."
                        .formatted(stock.getNameKo(), quarter(latest.fiscalPeriod())),
                quarter(latest.fiscalPeriod()), detail.getFinancialUpdatedAt());
    }

    private StockConceptAnalysisResponse investorFlow(Stock stock, DomesticStockDetailInfo detail) {
        InvestorTrade latest = first(detail.getInvestorTrades());
        if (latest == null) {
            return unavailable(stock, "현재 저장된 투자자별 매매 수급 데이터가 없습니다.");
        }
        return available(stock, metrics(
                        metric("외국인 순매수", signedShares(latest.foreignNetQuantity())),
                        metric("개인 순매수", signedShares(latest.personalNetQuantity())),
                        metric("기관 순매수", signedShares(latest.institutionNetQuantity())),
                        metric("종가", won(latest.closePrice()))),
                "외국인 %s − %s = %s · 개인 %s − %s = %s · 기관 %s − %s = %s".formatted(
                        shares(latest.foreignBuyQuantity()), shares(latest.foreignSellQuantity()),
                        signedShares(latest.foreignNetQuantity()), shares(latest.personalBuyQuantity()),
                        shares(latest.personalSellQuantity()), signedShares(latest.personalNetQuantity()),
                        shares(latest.institutionBuyQuantity()), shares(latest.institutionSellQuantity()),
                        signedShares(latest.institutionNetQuantity())),
                "%s의 %s 투자자별 순매수입니다. 양수는 해당 날짜에 매수가 매도보다 많았다는 뜻이고 음수는 그 반대입니다. 현재 보유 지분율이나 향후 주가 방향을 의미하지는 않습니다."
                        .formatted(stock.getNameKo(), date(latest.tradeDate())),
                date(latest.tradeDate()), detail.getInvestorUpdatedAt());
    }

    private StockConceptAnalysisResponse shortSellingAndLending(
            Stock stock, DomesticStockDetailInfo detail) {
        ShortSale shortSale = first(detail.getShortSales());
        LoanTransaction loan = first(detail.getLoanTransactions());
        if (shortSale == null && loan == null) {
            return unavailable(stock, "현재 저장된 공매도·대차 데이터가 없습니다.");
        }
        String interpretation = "공매도 거래는 실제로 빌린 주식을 시장에 판 흐름이고, 대차잔고는 아직 반환되지 않은 대여 주식의 잔량입니다. ";
        if (shortSale != null && shortSale.shortSaleVolumeRatio() != null) {
            interpretation += "%s의 최근 공매도 거래 비중은 %s입니다. ".formatted(
                    stock.getNameKo(), percent(shortSale.shortSaleVolumeRatio()));
        }
        if (loan != null && loan.loanBalanceQuantity() != null) {
            interpretation += "최근 대차잔고는 %s입니다. ".formatted(shares(loan.loanBalanceQuantity()));
        }
        interpretation += "두 값이 늘어도 모두 하락에 베팅했다고 단정할 수 없으며, 가격·거래량과 여러 거래일의 방향을 함께 봐야 합니다.";
        LocalDate referenceDate = shortSale != null ? shortSale.tradeDate() : loan.tradeDate();
        return available(stock, metrics(
                        metric("최근 공매도 수량", shortSale == null ? null : shares(shortSale.shortSaleQuantity())),
                        metric("공매도 거래 비중", shortSale == null ? null : percent(shortSale.shortSaleVolumeRatio())),
                        metric("대차 신규", loan == null ? null : shares(loan.newLoanQuantity())),
                        metric("대차 상환", loan == null ? null : shares(loan.redeemedLoanQuantity())),
                        metric("대차잔고", loan == null ? null : shares(loan.loanBalanceQuantity()))),
                "공매도 거래량은 실제 매도 흐름, 대차잔고는 빌린 뒤 아직 반환하지 않은 잔량",
                interpretation, date(referenceDate) + " 기준",
                oldest(detail.getShortSaleUpdatedAt(), detail.getLoanTransactionUpdatedAt()));
    }

    private StockConceptAnalysisResponse available(Stock stock,
                                                    List<Metric> metrics,
                                                    String formula,
                                                    String interpretation,
                                                    String reference,
                                                    LocalDateTime updatedAt) {
        if (metrics.isEmpty()) {
            return unavailable(stock, "설명에 필요한 실제 종목 데이터가 부족합니다.");
        }
        return new StockConceptAnalysisResponse(
                true,
                stock.getNameKo() + " 실제 데이터로 보기",
                metrics,
                formula,
                interpretation,
                reference,
                updatedAt(updatedAt),
                "한국투자증권 KIS",
                null
        );
    }

    private StockConceptAnalysisResponse unavailable(Stock stock, String reason) {
        return StockConceptAnalysisResponse.unavailable(stock.getNameKo() + " 실제 데이터로 보기", reason);
    }

    private static List<Metric> metrics(Metric... metrics) {
        return Arrays.stream(metrics).filter(Objects::nonNull).toList();
    }

    private static Metric metric(String label, String value) {
        return value == null ? null : new Metric(label, value);
    }

    private static <T> T first(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String comparisonSentence(String label, FinancialMetric metric) {
        if (metric == null || metric.ttm() == null || metric.runRate() == null) {
            return label + "은(는) TTM과 런레이트를 비교할 데이터가 부족합니다.";
        }
        if (metric.ttm().signum() <= 0) {
            return label + " TTM이 0 이하라 런레이트와의 증감률이 직관적이지 않습니다. 원래 금액을 함께 확인해야 합니다.";
        }
        BigDecimal differenceRate = metric.runRate().subtract(metric.ttm())
                .divide(metric.ttm().abs(), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (differenceRate.signum() == 0) {
            return label + " 런레이트와 TTM이 같은 수준입니다.";
        }
        String direction = differenceRate.signum() > 0 ? "높습니다" : "낮습니다";
        return "%s 런레이트는 TTM보다 %s %s. 계절성과 일회성 요인을 확인해야 이 흐름의 지속 여부를 판단할 수 있습니다."
                .formatted(label, percent(differenceRate.abs()), direction);
    }

    private static boolean isDomestic(Stock stock) {
        return stock.getMarketType() == StockMarketType.KOSPI
                || stock.getMarketType() == StockMarketType.KOSDAQ;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal ratio(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private static BigDecimal add(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : left.add(right);
    }

    private static String won(BigDecimal value) {
        return value == null ? null : DisplayFormatUtils.formatDecimal(value, 2) + "원";
    }

    private static String amount(BigDecimal value) {
        return value == null ? null : DisplayFormatUtils.formatDecimal(value, 2) + "억 원";
    }

    private static String multiple(BigDecimal value) {
        return value == null ? null : DisplayFormatUtils.formatDecimal(value, 2) + "배";
    }

    private static String percent(BigDecimal value) {
        return value == null ? null : DisplayFormatUtils.formatFixedDecimal(value, 2) + "%";
    }

    private static String signedPercent(BigDecimal value) {
        return value == null ? null : DisplayFormatUtils.formatSignedPercent(value, 2);
    }

    private static String shares(Long value) {
        return value == null ? null : DisplayFormatUtils.formatInteger(value) + "주";
    }

    private static String signedShares(Long value) {
        if (value == null) {
            return null;
        }
        String sign = value > 0 ? "+" : "";
        return sign + DisplayFormatUtils.formatInteger(value) + "주";
    }

    private static String quarter(LocalDate period) {
        if (period == null) {
            return null;
        }
        int quarter = (period.getMonthValue() - 1) / 3 + 1;
        return period.getYear() + "년 " + quarter + "분기";
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    private static String updatedAt(LocalDateTime value) {
        return value == null ? null : value.format(UPDATED_AT_FORMATTER);
    }

    private static LocalDateTime oldest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private enum MetricType {
        REVENUE("매출액") {
            @Override
            FinancialMetric extract(FinancialAnalysis analysis) {
                return analysis.revenue();
            }
        },
        OPERATING_PROFIT("영업이익") {
            @Override
            FinancialMetric extract(FinancialAnalysis analysis) {
                return analysis.operatingProfit();
            }
        },
        NET_INCOME("당기순이익") {
            @Override
            FinancialMetric extract(FinancialAnalysis analysis) {
                return analysis.netIncome();
            }
        };

        private final String displayName;

        MetricType(String displayName) {
            this.displayName = displayName;
        }

        abstract FinancialMetric extract(FinancialAnalysis analysis);
    }
}
