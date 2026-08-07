package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.metadata.domestic.DomesticStockBalanceSheet;
import com.finmate.domain.stock.metadata.domestic.DomesticStockCurrentQuote;
import com.finmate.domain.stock.metadata.domestic.DomesticStockDetailRefreshState;
import com.finmate.domain.stock.metadata.domestic.DomesticStockFinancialRatio;
import com.finmate.domain.stock.metadata.domestic.DomesticStockIncomeStatement;
import com.finmate.domain.stock.metadata.domestic.DomesticStockInvestorDailyTrade;
import com.finmate.global.format.DisplayFormatUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

// 종목 상세페이지의 시세/재무/투자 탭에 필요한 모든 상세정보를 담는 DTO
// 서로 다른 API로부터 받은 데이터들을 모아서 하나의 DTO에 담는 역할을 한다.
@Getter
public class DomesticStockDetailInfo {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final boolean supported;
    private final Quote quote; // 현재가, 등락률, PER, PBR, EPS, BPS, 시가총액등의 정보 저장
    private final List<FinancialRatio> financialRatios; // ROE, EPS, BPS, 매출증가율, 영업이익증가율, 부채비율 등
    private final List<IncomeStatement> incomeStatements; // 손익 계산표. 매출액, 영업이익, 경상이익, 당기순이익
    private final List<BalanceSheet> balanceSheets; // 대차대조표. 자산, 부채, 자본, 이익잉여금 등
    private final List<InvestorTrade> investorTrades; // 외국인, 개인, 기관 매수/매도/순매수 비율
    private final FinancialAnalysis financialAnalysis; // 매출, 영업이익, 순이익을 가지고, 지표를 분석하기 위한 레코드
    private final LocalDateTime quoteUpdatedAt;
    private final LocalDateTime financialUpdatedAt;
    private final LocalDateTime investorUpdatedAt;

    private DomesticStockDetailInfo(boolean supported,
                                    Quote quote,
                                    List<FinancialRatio> financialRatios,
                                    List<IncomeStatement> incomeStatements,
                                    List<BalanceSheet> balanceSheets,
                                    List<InvestorTrade> investorTrades,
                                    FinancialAnalysis financialAnalysis,
                                    LocalDateTime quoteUpdatedAt,
                                    LocalDateTime financialUpdatedAt,
                                    LocalDateTime investorUpdatedAt) {
        this.supported = supported;
        this.quote = quote;
        this.financialRatios = financialRatios;
        this.incomeStatements = incomeStatements;
        this.balanceSheets = balanceSheets;
        this.investorTrades = investorTrades;
        this.financialAnalysis = financialAnalysis;
        this.quoteUpdatedAt = quoteUpdatedAt;
        this.financialUpdatedAt = financialUpdatedAt;
        this.investorUpdatedAt = investorUpdatedAt;
    }

    public static DomesticStockDetailInfo unsupported() {
        return new DomesticStockDetailInfo(false, null, List.of(), List.of(), List.of(), List.of(), null,
                null, null, null);
    }

    // 여러 API를 통해 받은 데이터를 DomesticStockDetailInfo라는 하나의 DTO로 묶는다.
    public static DomesticStockDetailInfo of(DomesticStockCurrentQuote quote,
                                             DomesticStockCurrentQuoteSnapshot currentQuote,
                                             List<DomesticStockFinancialRatio> ratios,
                                             List<DomesticStockIncomeStatement> incomeStatements,
                                             List<DomesticStockBalanceSheet> balanceSheets,
                                             List<DomesticStockInvestorDailyTrade> investorTrades,
                                             DomesticStockDetailRefreshState refreshState) {
        // 손익계산서 엔티티 DomesticStockIncomeStatement를 화면용 DTO인 DomesticStockDetailInfo.IncomeStatement 로 변환한다.
        List<IncomeStatement> cumulativeIncomeStatements = incomeStatements.stream()
                .map(IncomeStatement::from)
                .sorted(Comparator.comparing(IncomeStatement::fiscalPeriod).reversed())
                .toList();
        // 분기 누적실적을 개별 분기 실적으로 변환한다.
        // KIS API의 분기 실적은 이전분기 누적이기 때문에, 이를 계산해서 각 분기별 실적으로 변환한다.
        List<IncomeStatement> quarterlyIncomeStatements = IncomeStatement.toStandaloneQuarters(
                cumulativeIncomeStatements);
        return new DomesticStockDetailInfo(
                true,
                currentQuote != null ? Quote.from(currentQuote) : quote == null ? null : Quote.from(quote),
                ratios.stream().map(FinancialRatio::from).toList(),
                quarterlyIncomeStatements.stream().limit(4).toList(), // 화면에는 최근 4개의 분기 실적만 공개한다.
                balanceSheets.stream().map(BalanceSheet::from).toList(),
                investorTrades.stream().map(InvestorTrade::from).toList(),
                FinancialAnalysis.from(quarterlyIncomeStatements),
                // 현재가는 Redis에 캐싱된 데이터가 있으면 해당 데이터를 사용하고, 없으면 DB에 저장된 데이터를 사용한다.
                currentQuote != null ? currentQuote.fetchedAt()
                        : refreshState == null ? null : refreshState.getCurrentQuoteUpdatedAt(),
                financialUpdatedAt(refreshState),
                refreshState == null ? null : refreshState.getInvestorTradeUpdatedAt());
    }

    public boolean hasQuote() {
        return quote != null;
    }

    public boolean hasFinancialData() {
        return !financialRatios.isEmpty() || !incomeStatements.isEmpty() || !balanceSheets.isEmpty();
    }

    public boolean hasFinancialAnalysis() {
        return financialAnalysis != null;
    }

    public boolean hasInvestorTrades() {
        return !investorTrades.isEmpty();
    }

    public String formatNumber(BigDecimal value) {
        return DisplayFormatUtils.formatDecimal(value, 2);
    }

    public String formatPercent(BigDecimal value) {
        return value == null ? "-" : DisplayFormatUtils.formatFixedDecimal(value, 2) + "%";
    }

    public String formatSignedPercent(BigDecimal value) {
        return DisplayFormatUtils.formatSignedPercent(value, 2);
    }

    public String formatInteger(Long value) {
        return DisplayFormatUtils.formatInteger(value);
    }

    public String formatSignedInteger(Long value) {
        if (value == null) {
            return "-";
        }
        String sign = value > 0 ? "+" : "";
        return sign + DisplayFormatUtils.formatInteger(value);
    }

    public String formatDate(LocalDate value) {
        return DisplayFormatUtils.formatDate(value, DATE_FORMATTER);
    }

    public String formatPeriod(LocalDate value) {
        return DisplayFormatUtils.formatDate(value, PERIOD_FORMATTER);
    }

    public String formatQuarter(LocalDate value) {
        if (value == null) {
            return "-";
        }
        int quarter = (value.getMonthValue() - 1) / 3 + 1;
        return value.getYear() + "년 " + quarter + "분기";
    }

    public String formatUpdatedAt(LocalDateTime value) {
        return DisplayFormatUtils.formatDate(value, UPDATED_AT_FORMATTER);
    }

    public String directionClass(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "neutral";
        }
        return value.signum() > 0 ? "positive" : "negative";
    }

    public String directionClass(Long value) {
        if (value == null || value == 0L) {
            return "neutral";
        }
        return value > 0 ? "positive" : "negative";
    }

    private static LocalDateTime financialUpdatedAt(DomesticStockDetailRefreshState state) {
        if (state == null) {
            return null;
        }
        return oldestComplete(state.getFinancialRatioUpdatedAt(), state.getIncomeStatementUpdatedAt(),
                state.getBalanceSheetUpdatedAt());
    }

    private static LocalDateTime oldestComplete(LocalDateTime... values) {
        LocalDateTime oldest = null;
        for (LocalDateTime value : values) {
            if (value == null) {
                return null;
            }
            if (oldest == null || value.isBefore(oldest)) {
                oldest = value;
            }
        }
        return oldest;
    }

    public record Quote(
            BigDecimal currentPrice,
            BigDecimal changeAmount,
            BigDecimal changeRate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            Long accumulatedVolume,
            BigDecimal accumulatedTradeAmount,
            BigDecimal per,
            BigDecimal pbr,
            BigDecimal eps,
            BigDecimal bps,
            BigDecimal marketCap,
            Long listedShares,
            BigDecimal w52HighPrice,
            LocalDate w52HighDate,
            BigDecimal w52HighRate,
            BigDecimal w52LowPrice,
            LocalDate w52LowDate,
            BigDecimal w52LowRate,
            Long foreignHoldingQuantity,
            BigDecimal foreignExhaustionRate,
            Long foreignNetBuyQuantity,
            BigDecimal totalLoanBalanceRate) {
        static Quote from(DomesticStockCurrentQuote quote) {
            return new Quote(quote.getCurrentPrice(), quote.getChangeAmount(), quote.getChangeRate(),
                    quote.getOpenPrice(), quote.getHighPrice(), quote.getLowPrice(),
                    quote.getAccumulatedVolume(), quote.getAccumulatedTradeAmount(), quote.getPer(),
                    quote.getPbr(), quote.getEps(), quote.getBps(), quote.getMarketCap(),
                    quote.getListedShares(), quote.getW52HighPrice(), quote.getW52HighDate(),
                    quote.getW52HighRate(), quote.getW52LowPrice(), quote.getW52LowDate(),
                    quote.getW52LowRate(), quote.getForeignHoldingQuantity(),
                    quote.getForeignExhaustionRate(), quote.getForeignNetBuyQuantity(),
                    quote.getTotalLoanBalanceRate());
        }

        static Quote from(DomesticStockCurrentQuoteSnapshot quote) {
            return new Quote(quote.currentPrice(), quote.changeAmount(), quote.changeRate(),
                    quote.openPrice(), quote.highPrice(), quote.lowPrice(), quote.accumulatedVolume(),
                    quote.accumulatedTradeAmount(), quote.per(), quote.pbr(), quote.eps(), quote.bps(),
                    quote.marketCap(), quote.listedShares(), quote.w52HighPrice(), quote.w52HighDate(),
                    quote.w52HighRate(), quote.w52LowPrice(), quote.w52LowDate(), quote.w52LowRate(),
                    quote.foreignHoldingQuantity(), quote.foreignExhaustionRate(),
                    quote.foreignNetBuyQuantity(), quote.totalLoanBalanceRate());
        }
    }

    public record FinancialRatio(LocalDate fiscalPeriod,
                                 BigDecimal salesGrowthRate,
                                 BigDecimal operatingProfitGrowthRate,
                                 BigDecimal netIncomeGrowthRate,
                                 BigDecimal roe,
                                 BigDecimal eps,
                                 BigDecimal bps,
                                 BigDecimal reserveRate,
                                 BigDecimal debtRate) {
        static FinancialRatio from(DomesticStockFinancialRatio ratio) {
            return new FinancialRatio(ratio.getFiscalPeriod(), ratio.getSalesGrowthRate(),
                    ratio.getOperatingProfitGrowthRate(), ratio.getNetIncomeGrowthRate(), ratio.getRoe(),
                    ratio.getEps(), ratio.getBps(), ratio.getReserveRate(), ratio.getDebtRate());
        }
    }

    public record IncomeStatement(LocalDate fiscalPeriod,
                                  BigDecimal revenue,
                                  BigDecimal operatingProfit,
                                  BigDecimal ordinaryProfit,
                                  BigDecimal netIncome) {
        static IncomeStatement from(DomesticStockIncomeStatement statement) {
            return new IncomeStatement(statement.getFiscalPeriod(), statement.getRevenue(),
                    statement.getOperatingProfit(), statement.getOrdinaryProfit(), statement.getNetIncome());
        }

        // 분기 누적 실적을 각 분기별 실적으로 변환하여 DTO로 저장한다.
        static List<IncomeStatement> toStandaloneQuarters(List<IncomeStatement> cumulativeStatements) {
            if (cumulativeStatements == null || cumulativeStatements.isEmpty()) {
                return List.of();
            }
            List<IncomeStatement> sorted = cumulativeStatements.stream()
                    .sorted(Comparator.comparing(IncomeStatement::fiscalPeriod).reversed())
                    .toList();
            List<IncomeStatement> quarterly = new ArrayList<>();
            for (IncomeStatement current : sorted) {
                // 3월, 즉 1분기의 경우에는 그대로 사용한다.
                if (current.fiscalPeriod().getMonthValue() == 3) {
                    quarterly.add(current);
                    continue;
                }
                // 직전 분기의 누적실적을 찾는다.
                IncomeStatement previousCumulative = FinancialAnalysis.findByPeriod(
                        sorted, current.fiscalPeriod().minusMonths(3));
                if (previousCumulative == null
                        || previousCumulative.fiscalPeriod().getYear() != current.fiscalPeriod().getYear()) {
                    continue;
                }
                // 현재 분기 누적실적에서 직전 분기의 누적실적을 차감한다 -> 현재 분기 단독실적이 계산된다.
                quarterly.add(new IncomeStatement(
                        current.fiscalPeriod(),
                        subtract(current.revenue(), previousCumulative.revenue()),
                        subtract(current.operatingProfit(), previousCumulative.operatingProfit()),
                        subtract(current.ordinaryProfit(), previousCumulative.ordinaryProfit()),
                        subtract(current.netIncome(), previousCumulative.netIncome())));
            }
            return List.copyOf(quarterly);
        }

        // 현재 분기 누적 실적 - 직전 분기 누적 실적을 계산하는 메서드
        private static BigDecimal subtract(BigDecimal cumulative, BigDecimal previousCumulative) {
            return cumulative == null || previousCumulative == null
                    ? null
                    : cumulative.subtract(previousCumulative);
        }
    }

    // 매출, 영업이익, 순이익을 가지고, 지표를 분석하기 위한 레코드
    public record FinancialAnalysis(LocalDate latestPeriod,
                                    FinancialMetric revenue, // 매출 분석
                                    FinancialMetric operatingProfit, // 영업이익 분석
                                    FinancialMetric netIncome) { // 당기 순이익 분석
        private static final int REQUIRED_TTM_QUARTERS = 4;

        // 가장 최신 분기를 찾는다.
        static FinancialAnalysis from(List<IncomeStatement> statements) {
            if (statements == null || statements.isEmpty()) {
                return null;
            }
            List<IncomeStatement> sorted = statements.stream()
                    .sorted(Comparator.comparing(IncomeStatement::fiscalPeriod).reversed())
                    .toList();
            IncomeStatement latest = sorted.get(0); // 가장 최신 분기 찾기
            // 직전 분기를 찾는다.
            IncomeStatement previousQuarter = findByPeriod(sorted, latest.fiscalPeriod().minusMonths(3));
            // 전년 동기를 찾는다.
            IncomeStatement previousYearQuarter = findByPeriod(sorted, latest.fiscalPeriod().minusYears(1));
            // TTM은 최근 4분기를 찾는다.
            List<IncomeStatement> ttmStatements = consecutiveTtmStatements(sorted, latest.fiscalPeriod());

            return new FinancialAnalysis(
                    latest.fiscalPeriod(), // 최신 분기 매출
                    // 매출, 영업이익, 당기순이익에 대해서 경제 지표를 각각 계산한다.
                    metric(latest.revenue(), value(previousQuarter, IncomeStatement::revenue),
                            value(previousYearQuarter, IncomeStatement::revenue),
                            ttmStatements, IncomeStatement::revenue),
                    metric(latest.operatingProfit(), value(previousQuarter, IncomeStatement::operatingProfit),
                            value(previousYearQuarter, IncomeStatement::operatingProfit),
                            ttmStatements, IncomeStatement::operatingProfit),
                    metric(latest.netIncome(), value(previousQuarter, IncomeStatement::netIncome),
                            value(previousYearQuarter, IncomeStatement::netIncome),
                            ttmStatements, IncomeStatement::netIncome));
        }

        // 실제 경제 지표를 계산하는 메서드
        private static FinancialMetric metric(BigDecimal latest,
                                              BigDecimal previousQuarter,
                                              BigDecimal previousYearQuarter,
                                              List<IncomeStatement> ttmStatements,
                                              Function<IncomeStatement, BigDecimal> extractor) {
            return new FinancialMetric(
                    latest, // 최신 분기 실적
                    growthRate(latest, previousYearQuarter), // yoyRate: 전년 동기 대비 성장률
                    growthRate(latest, previousQuarter), // qoqRate: 직전 분기 대비 성장률
                    sum(ttmStatements, extractor), // ttm: 최근 연속 4분기 실적 합계
                    latest == null ? null : latest.multiply(BigDecimal.valueOf(4))); // run rate: 최신 분기 * 4
        }

        // 직전 분기 or 전년 분기 대비 성장률을 계산한다.
        private static BigDecimal growthRate(BigDecimal current, BigDecimal comparison) {
            if (current == null || comparison == null || comparison.signum() <= 0) {
                return null;
            }
            return current.subtract(comparison)
                    .divide(comparison, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // 최신 분기부터 시작하여 이전 3개분기를 연속적으로 찾고, 이를 리스트에 담는다.
        private static List<IncomeStatement> consecutiveTtmStatements(List<IncomeStatement> statements,
                                                                       LocalDate latestPeriod) {
            List<IncomeStatement> result = new ArrayList<>(REQUIRED_TTM_QUARTERS);
            for (int index = 0; index < REQUIRED_TTM_QUARTERS; index++) {
                IncomeStatement statement = findByPeriod(statements, latestPeriod.minusMonths(index * 3L));
                if (statement == null) {
                    return List.of();
                }
                result.add(statement);
            }
            return result;
        }

        // 리스트에 담겨있는 분기들의 실적들을 모두 누적해서 더한다.
        private static BigDecimal sum(List<IncomeStatement> statements,
                                      Function<IncomeStatement, BigDecimal> extractor) {
            if (statements.size() != REQUIRED_TTM_QUARTERS) {
                return null;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (IncomeStatement statement : statements) {
                BigDecimal value = extractor.apply(statement);
                if (value == null) {
                    return null;
                }
                total = total.add(value);
            }
            return total;
        }
        // 분기 결산월을 파악하기 위한 메서드
        private static IncomeStatement findByPeriod(List<IncomeStatement> statements, LocalDate period) {
            YearMonth targetPeriod = YearMonth.from(period);
            return statements.stream()
                    .filter(statement -> YearMonth.from(statement.fiscalPeriod()).equals(targetPeriod))
                    .findFirst()
                    .orElse(null);
        }

        private static BigDecimal value(IncomeStatement statement,
                                        Function<IncomeStatement, BigDecimal> extractor) {
            return statement == null ? null : extractor.apply(statement);
        }
    }

    // 경제 지표
    public record FinancialMetric(BigDecimal latestQuarter, // 가장 최신 분기 실적
                                  BigDecimal yoyRate, // year over year: 전년 동기 대비 성장률 (작년 같은 분기 대비 성장률)
                                  BigDecimal qoqRate, // quarter over quarter: 직전 분기 대비 성장률
                                  BigDecimal ttm, // Trailing Twelve Months: 최근 4개 분기의 누적 실적
                                  BigDecimal runRate) { // RunRate: 최근 분기 실적에 4를 곱한 값. 즉 최근 분기 실적이 계속 반복되었을때 기대할 수 있는 연간실적
    }

    public record BalanceSheet(LocalDate fiscalPeriod,
                               BigDecimal currentAssets,
                               BigDecimal totalAssets,
                               BigDecimal currentLiabilities,
                               BigDecimal totalLiabilities,
                               BigDecimal capital,
                               BigDecimal retainedEarnings,
                               BigDecimal totalEquity) {
        static BalanceSheet from(DomesticStockBalanceSheet sheet) {
            return new BalanceSheet(sheet.getFiscalPeriod(), sheet.getCurrentAssets(), sheet.getTotalAssets(),
                    sheet.getCurrentLiabilities(), sheet.getTotalLiabilities(), sheet.getCapital(),
                    sheet.getRetainedEarnings(), sheet.getTotalEquity());
        }
    }

    public record InvestorTrade(LocalDate tradeDate,
                                BigDecimal closePrice,
                                Long foreignBuyQuantity,
                                Long foreignSellQuantity,
                                Long foreignNetQuantity,
                                Long personalBuyQuantity,
                                Long personalSellQuantity,
                                Long personalNetQuantity,
                                Long institutionBuyQuantity,
                                Long institutionSellQuantity,
                                Long institutionNetQuantity) {
        static InvestorTrade from(DomesticStockInvestorDailyTrade trade) {
            return new InvestorTrade(trade.getTradeDate(), trade.getClosePrice(),
                    trade.getForeignBuyQuantity(), trade.getForeignSellQuantity(), trade.getForeignNetQuantity(),
                    trade.getPersonalBuyQuantity(), trade.getPersonalSellQuantity(), trade.getPersonalNetQuantity(),
                    trade.getInstitutionBuyQuantity(), trade.getInstitutionSellQuantity(),
                    trade.getInstitutionNetQuantity());
        }
    }
}
