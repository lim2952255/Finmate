package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.metadata.domestic.DomesticFinancialPeriodType;
import com.finmate.domain.stock.metadata.domestic.DomesticStockBalanceSheet;
import com.finmate.domain.stock.metadata.domestic.DomesticStockCurrentQuote;
import com.finmate.domain.stock.metadata.domestic.DomesticStockDetailRefreshState;
import com.finmate.domain.stock.metadata.domestic.DomesticStockFinancialRatio;
import com.finmate.domain.stock.metadata.domestic.DomesticStockIncomeStatement;
import com.finmate.domain.stock.metadata.domestic.DomesticStockInvestorDailyTrade;
import com.finmate.infra.kis.parser.KisValueParser;
import com.finmate.infra.kis.stock.detail.FinancialPeriod;
import com.finmate.infra.kis.stock.detail.KisBalanceSheetResponse;
import com.finmate.infra.kis.stock.detail.KisDomesticStockDetailClient;
import com.finmate.infra.kis.stock.detail.KisFinancialRatioResponse;
import com.finmate.infra.kis.stock.detail.KisIncomeStatementResponse;
import com.finmate.infra.kis.stock.detail.KisInvestorTradeResponse;
import com.finmate.repository.stock.metadata.domestic.DomesticStockBalanceSheetRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockCurrentQuoteRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockDetailRefreshStateRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockFinancialRatioRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockIncomeStatementRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockInvestorDailyTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// 종목 상세페이지 접속시, 종목 상세정보가 최신화가 되어있는지를 검사하고, 최신화되어있지 않다면 KIS API를 호출해서 갱신한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockDetailRefreshService {
    private static final String KRX_MARKET_CODE = "J"; //
    private static final DomesticFinancialPeriodType PERIOD_TYPE = DomesticFinancialPeriodType.QUARTERLY; // DB에 저장할때 사용하는 enum
    private static final FinancialPeriod KIS_PERIOD = FinancialPeriod.QUARTERLY; // KIS API 요청용 enum

    private final KisDomesticStockDetailClient kisClient;
    private final DomesticStockCurrentQuoteRepository currentQuoteRepository;
    private final DomesticStockFinancialRatioRepository financialRatioRepository;
    private final DomesticStockIncomeStatementRepository incomeStatementRepository;
    private final DomesticStockBalanceSheetRepository balanceSheetRepository;
    private final DomesticStockInvestorDailyTradeRepository investorDailyTradeRepository;
    private final DomesticStockDetailRefreshStateRepository refreshStateRepository;
    private final DomesticStockCurrentQuoteCacheService currentQuoteCacheService;

    // 갱신 lock
    // 이때 종목별로 서로 다른 lock을 관리하기 위해 Lock을 HashMap으로 관리한다.
    private final ConcurrentHashMap<Long, Object> stockRefreshLocks = new ConcurrentHashMap<>();

    // 데이터별 갱신 주기
    @Value("${finmate.stock-detail.current-quote-cache-seconds:10}")
    private long currentQuoteCacheSeconds; // 현재가는 매우 자주 바뀌기 때문 10초로 짧게 캐싱한다.

    @Value("${finmate.stock-detail.financial-refresh-seconds:86400}")
    private long financialRefreshSeconds; // 재무정보는 거의 바뀌지 않으므로 24시간로 설정한다.

    @Value("${finmate.stock-detail.investor-refresh-seconds:600}")
    private long investorRefreshSeconds; // 투자자 동향은 현재가만큼은 아니지만, 자주 바뀌기 때문에 10분마다 갱신한다.

    // 정보 갱신이 필요한지를 검사한다.
    public DomesticStockCurrentQuoteSnapshot refreshIfNeeded(Stock stock, LocalDate investorBaseDate) {
        if (!isDomestic(stock)) {
            return null;
        }
        // 같은 종목을 여러 사용자가 동시에 조회했을 때, KIS API를 중복호출하는 것을 방지하기 위해 JVM Lock을 사용한다.
        // 즉 사용자 A,B,C가 동시에 조회했을때 KIS API요청을 3번 보내는 것을 방지하기 위해, Lock을 걸어 하나의 KIS API 요청만 보내고, 나머지는 대기시킨다.
        Object lock = stockRefreshLocks.computeIfAbsent(stock.getId(), ignored -> new Object());
        synchronized (lock) {
            // 갱신이 필요한지를 검사하고, 실제로 갱신을 수행한다.
            return refreshUnderLock(stock, investorBaseDate);
        }
    }

    // 상세정보 갱신이 필요한지 검사하고, 갱신이 필요하면 KIS API를 호출해서 실제로 갱신한다.
    private DomesticStockCurrentQuoteSnapshot refreshUnderLock(Stock stock, LocalDate investorBaseDate) {
        // 특정 종목의 상세정보 갱신시각이 저장되어 있는 RefreshStateRepository에서 엔티티를 조회한다.
        DomesticStockDetailRefreshState state = refreshStateRepository.findByStock_Id(stock.getId())
                .orElseGet(() -> DomesticStockDetailRefreshState.create(stock));
        LocalDateTime now = LocalDateTime.now();
        // 현재가 처리 로직
        DomesticStockCurrentQuoteSnapshot currentQuote = resolveCurrentQuote(stock, state, now);

        // 재무 비율이 지정한 기간보다 오래되면, KIS API를 호출해서 정보를 갱신한다.
        Duration financialFreshness = Duration.ofSeconds(financialRefreshSeconds);
        if (!financialRatioRepository.existsByStock_IdAndPeriodType(stock.getId(), PERIOD_TYPE)
                || isStale(state.getFinancialRatioUpdatedAt(), financialFreshness, now)) {
            refreshSafely(stock, "재무비율", () -> {
                if (saveFinancialRatios(stock, kisClient.fetchFinancialRatios(stock.getSymbol(), KIS_PERIOD))) {
                    state.markFinancialRatioUpdated(LocalDateTime.now());
                    refreshStateRepository.save(state);
                }
            });
        }
        // 손익계산서가 지정한 기간보다 오래되면, KIS API를 호출해서 정보를 갱신한다.
        if (!incomeStatementRepository.existsByStock_IdAndPeriodType(stock.getId(), PERIOD_TYPE)
                || isStale(state.getIncomeStatementUpdatedAt(), financialFreshness, now)) {
            refreshSafely(stock, "손익계산서", () -> {
                if (saveIncomeStatements(stock, kisClient.fetchIncomeStatements(stock.getSymbol(), KIS_PERIOD))) {
                    state.markIncomeStatementUpdated(LocalDateTime.now());
                    refreshStateRepository.save(state);
                }
            });
        }
        // 대차대조표가 지정한 기간보다 오래되면, KIS API를 호출해서 정보를 갱신한다.
        if (!balanceSheetRepository.existsByStock_IdAndPeriodType(stock.getId(), PERIOD_TYPE)
                || isStale(state.getBalanceSheetUpdatedAt(), financialFreshness, now)) {
            refreshSafely(stock, "대차대조표", () -> {
                if (saveBalanceSheets(stock, kisClient.fetchBalanceSheets(stock.getSymbol(), KIS_PERIOD))) {
                    state.markBalanceSheetUpdated(LocalDateTime.now());
                    refreshStateRepository.save(state);
                }
            });
        }
        // 투자자 수급 데이터가 지정한 기간보다 오래되면, KIS API를 호출해서 정보를 갱신한다.
        if (isInvestorTradeStale(stock, state.getInvestorTradeUpdatedAt(), now)) {
            refreshSafely(stock, "투자자 매매동향", () -> {
                if (saveInvestorTrades(stock, kisClient.fetchDailyInvestorTrades(stock.getSymbol(), investorBaseDate))) {
                    state.markInvestorTradeUpdated(LocalDateTime.now());
                    refreshStateRepository.save(state);
                }
            });
        }
        return currentQuote;
    }

    // 현재가 걍신 처리 로직
    private DomesticStockCurrentQuoteSnapshot resolveCurrentQuote(Stock stock,
                                                                  DomesticStockDetailRefreshState state,
                                                                  LocalDateTime now) {
        // 캐싱 시간
        Duration cacheTtl = Duration.ofSeconds(currentQuoteCacheSeconds);
        // 현재 장중인지를 검사한다.
        // 장중이라면 현재가가 실시간으로 바뀌기 때문에, 매번 DB에 저장하지 않고, Redis에 캐싱한다.
        if (StockMarketSchedules.isTradingTimeNow(stock)) {
            // 장중이라면 Redis에 캐싱된 데이터가 있다면 해당 데이터를 리턴하고, 없다면 KIS API를 호출해서 응답데이터를 Redis에 캐싱한다.
            return currentQuoteCacheService.get(stock.getSymbol(), cacheTtl)
                    .orElseGet(() -> fetchAndCacheCurrentQuote(stock, cacheTtl));
        }

        // 장중이 아니라면 마지막 현재가를 DB에 저장한다.
        LocalDate expectedClosingDate = StockMarketSchedules.expectedLatestDailyPriceTradeDate(stock.getMarketType()); // DB에 저장되어 있어야할 가장 최근 거래일 계산
        LocalDateTime requiredSnapshotAt = expectedClosingDate.atTime(
                StockMarketSchedules.getSchedule(stock.getMarketType()).dailyPriceAvailableTime());
        // 만약 이미 DB에 최신 데이터가 저장되어 있다면 해당 데이터를 Repository애서 조회하여 바로 return한다.
        if (state.getCurrentQuoteUpdatedAt() != null
                && !state.getCurrentQuoteUpdatedAt().isBefore(requiredSnapshotAt)) {
            return currentQuoteRepository.findByStock_Id(stock.getId())
                    .map(DomesticStockCurrentQuoteSnapshot::from)
                    .orElse(null);
        }

        // 만약 DB에 최신 종가가 저장되어 있지 않다면 캐시에 데이터가 저장되어 있는지 보고, 데이터가 저장되어 있다면  해당 데이터를 리턴하고,  캐시에 데이터가 저장되어 있지 않다면 KIS API를 호출해서 데이터를 DB에 저장하고, 결과 데이터를 리턴한다.
        DomesticStockCurrentQuoteSnapshot snapshot = currentQuoteCacheService.get(stock.getSymbol(), cacheTtl)
                .orElseGet(() -> fetchAndCacheCurrentQuote(stock, cacheTtl));
        if (snapshot != null && saveCurrentQuote(stock, snapshot)) {
            // 만약 KIS API를 호출해서 데이터를 갱신했다면, 갱신일자를 update한다.
            state.markCurrentQuoteUpdated(now);
            refreshStateRepository.save(state);
        }
        return snapshot;
    }

    // KIS API를 호출해서 현재가 시세 데이터를 갱신한다.
    private DomesticStockCurrentQuoteSnapshot fetchAndCacheCurrentQuote(Stock stock, Duration cacheTtl) {
        try {
            DomesticStockCurrentQuoteSnapshot snapshot = DomesticStockCurrentQuoteSnapshot.from(
                    kisClient.fetchCurrentPrice(stock.getSymbol()), LocalDateTime.now());
            // KIS API를 통해 조회한 데이터를 Redis에 캐싱한다. (캐싱 시간: 10초)
            currentQuoteCacheService.put(stock.getSymbol(), snapshot, cacheTtl);
            return snapshot;
        } catch (RuntimeException e) {
            log.warn("국내 종목 상세 현재가 갱신 실패. stockId={}, symbol={}",
                    stock.getId(), stock.getSymbol(), e);
            return currentQuoteRepository.findByStock_Id(stock.getId())
                    .map(DomesticStockCurrentQuoteSnapshot::from)
                    .orElse(null);
        }
    }

    // 새로 조회한 현재가 데이터를 기반으로 DB에 새로 저장하거나, update를 수행한다.
    private boolean saveCurrentQuote(Stock stock, DomesticStockCurrentQuoteSnapshot item) {
        DomesticStockCurrentQuote quote = currentQuoteRepository.findByStock_Id(stock.getId())
                .orElseGet(() -> DomesticStockCurrentQuote.create(
                        stock, item.currentPrice(), item.changeAmount(), item.changeSign(), item.changeRate(),
                        item.openPrice(), item.highPrice(), item.lowPrice(), item.accumulatedVolume(),
                        item.accumulatedTradeAmount(), item.per(), item.pbr(), item.eps(), item.bps(),
                        item.marketCap(), item.listedShares(), item.w52HighPrice(), item.w52HighDate(),
                        item.w52HighRate(), item.w52LowPrice(), item.w52LowDate(), item.w52LowRate(),
                        item.foreignHoldingQuantity(), item.foreignExhaustionRate(),
                        item.foreignNetBuyQuantity(), item.totalLoanBalanceRate()));

        if (quote.getId() != null) {
            quote.update(item.currentPrice(), item.changeAmount(), item.changeSign(), item.changeRate(),
                    item.openPrice(), item.highPrice(), item.lowPrice(), item.accumulatedVolume(),
                    item.accumulatedTradeAmount(), item.per(), item.pbr(), item.eps(), item.bps(),
                    item.marketCap(), item.listedShares(), item.w52HighPrice(), item.w52HighDate(),
                    item.w52HighRate(), item.w52LowPrice(), item.w52LowDate(), item.w52LowRate(),
                    item.foreignHoldingQuantity(), item.foreignExhaustionRate(),
                    item.foreignNetBuyQuantity(), item.totalLoanBalanceRate());
        }
        currentQuoteRepository.save(quote);
        return true;
    }
    // 새로 조회한 재무비율 데이터를 기반으로 DB에 새로 저장하거나, update를 수행한다.
    private boolean saveFinancialRatios(Stock stock, KisFinancialRatioResponse response) {
        boolean saved = false;
        for (KisFinancialRatioResponse.FinancialRatio item : safeList(response == null ? null : response.output())) {
            LocalDate fiscalPeriod = fiscalPeriod(item.settlementYearMonth());
            if (fiscalPeriod == null) {
                continue;
            }
            DomesticStockFinancialRatio ratio = financialRatioRepository
                    .findByStock_IdAndPeriodTypeAndFiscalPeriod(stock.getId(), PERIOD_TYPE, fiscalPeriod)
                    .orElseGet(() -> DomesticStockFinancialRatio.create(
                            stock, PERIOD_TYPE, fiscalPeriod, decimal(item.salesGrowthRate()),
                            decimal(item.operatingProfitGrowthRate()), decimal(item.netIncomeGrowthRate()),
                            decimal(item.roe()), decimal(item.eps()), decimal(item.salesPerShare()),
                            decimal(item.bps()), decimal(item.reserveRatio()), decimal(item.debtRatio())));
            if (ratio.getId() != null) {
                ratio.update(decimal(item.salesGrowthRate()), decimal(item.operatingProfitGrowthRate()),
                        decimal(item.netIncomeGrowthRate()), decimal(item.roe()), decimal(item.eps()),
                        decimal(item.salesPerShare()), decimal(item.bps()), decimal(item.reserveRatio()),
                        decimal(item.debtRatio()));
            }
            financialRatioRepository.save(ratio);
            saved = true;
        }
        return saved;
    }

    // 새로 조회한 손익계산서 데이터를 기반으로 DB에 새로 저장하거나, update를 수행한다.
    private boolean saveIncomeStatements(Stock stock, KisIncomeStatementResponse response) {
        boolean saved = false;
        for (KisIncomeStatementResponse.IncomeStatement item : safeList(response == null ? null : response.output())) {
            LocalDate fiscalPeriod = fiscalPeriod(item.settlementYearMonth());
            if (fiscalPeriod == null) {
                continue;
            }
            DomesticStockIncomeStatement statement = incomeStatementRepository
                    .findByStock_IdAndPeriodTypeAndFiscalPeriod(stock.getId(), PERIOD_TYPE, fiscalPeriod)
                    .orElseGet(() -> DomesticStockIncomeStatement.create(
                            stock, PERIOD_TYPE, fiscalPeriod, decimal(item.revenue()), decimal(item.costOfSales()),
                            decimal(item.grossProfit()), decimal(item.depreciationExpense()),
                            decimal(item.sellingAndAdministrativeExpense()), decimal(item.operatingProfit()),
                            decimal(item.nonOperatingIncome()), decimal(item.nonOperatingExpense()),
                            decimal(item.ordinaryProfit()), decimal(item.extraordinaryProfit()),
                            decimal(item.extraordinaryLoss()), decimal(item.netIncome())));
            if (statement.getId() != null) {
                statement.update(decimal(item.revenue()), decimal(item.costOfSales()), decimal(item.grossProfit()),
                        decimal(item.depreciationExpense()), decimal(item.sellingAndAdministrativeExpense()),
                        decimal(item.operatingProfit()), decimal(item.nonOperatingIncome()),
                        decimal(item.nonOperatingExpense()), decimal(item.ordinaryProfit()),
                        decimal(item.extraordinaryProfit()), decimal(item.extraordinaryLoss()),
                        decimal(item.netIncome()));
            }
            incomeStatementRepository.save(statement);
            saved = true;
        }
        return saved;
    }

    // 새로 조회한 재무상태표 데이터를 기반으로 DB에 새로 저장하거나, update를 수행한다.
    private boolean saveBalanceSheets(Stock stock, KisBalanceSheetResponse response) {
        boolean saved = false;
        for (KisBalanceSheetResponse.BalanceSheet item : safeList(response == null ? null : response.output())) {
            LocalDate fiscalPeriod = fiscalPeriod(item.settlementYearMonth());
            if (fiscalPeriod == null) {
                continue;
            }
            DomesticStockBalanceSheet sheet = balanceSheetRepository
                    .findByStock_IdAndPeriodTypeAndFiscalPeriod(stock.getId(), PERIOD_TYPE, fiscalPeriod)
                    .orElseGet(() -> DomesticStockBalanceSheet.create(
                            stock, PERIOD_TYPE, fiscalPeriod, decimal(item.currentAssets()),
                            decimal(item.nonCurrentAssets()), decimal(item.totalAssets()),
                            decimal(item.currentLiabilities()), decimal(item.nonCurrentLiabilities()),
                            decimal(item.totalLiabilities()), decimal(item.capitalStock()),
                            decimal(item.capitalSurplus()), decimal(item.retainedEarnings()),
                            decimal(item.totalEquity())));
            if (sheet.getId() != null) {
                sheet.update(decimal(item.currentAssets()), decimal(item.nonCurrentAssets()),
                        decimal(item.totalAssets()), decimal(item.currentLiabilities()),
                        decimal(item.nonCurrentLiabilities()), decimal(item.totalLiabilities()),
                        decimal(item.capitalStock()), decimal(item.capitalSurplus()),
                        decimal(item.retainedEarnings()), decimal(item.totalEquity()));
            }
            balanceSheetRepository.save(sheet);
            saved = true;
        }
        return saved;
    }

    // 새로 조회한 투자자 수급 데이터를 저장하거나 업데이트한다.
    private boolean saveInvestorTrades(Stock stock, KisInvestorTradeResponse response) {
        boolean saved = false;
        for (KisInvestorTradeResponse.DailyInvestorTrade item : safeList(response == null ? null : response.output2())) {
            LocalDate tradeDate = date(item.tradeDate());
            if (tradeDate == null) {
                continue;
            }
            DomesticStockInvestorDailyTrade trade = investorDailyTradeRepository
                    .findByStock_IdAndMarketCodeAndTradeDate(stock.getId(), KRX_MARKET_CODE, tradeDate)
                    .orElseGet(() -> DomesticStockInvestorDailyTrade.create(
                            stock, KRX_MARKET_CODE, tradeDate, decimal(item.closePrice()),
                            longValue(item.accumulatedVolume()), decimal(item.accumulatedTradeAmount()),
                            longValue(item.foreignBuyQuantity()), longValue(item.foreignSellQuantity()),
                            longValue(item.foreignNetBuyQuantity()), decimal(item.foreignBuyAmount()),
                            decimal(item.foreignSellAmount()), decimal(item.foreignNetBuyAmount()),
                            longValue(item.personalBuyQuantity()), longValue(item.personalSellQuantity()),
                            longValue(item.personalNetBuyQuantity()), decimal(item.personalBuyAmount()),
                            decimal(item.personalSellAmount()), decimal(item.personalNetBuyAmount()),
                            longValue(item.institutionBuyQuantity()), longValue(item.institutionSellQuantity()),
                            longValue(item.institutionNetBuyQuantity()), decimal(item.institutionBuyAmount()),
                            decimal(item.institutionSellAmount()), decimal(item.institutionNetBuyAmount())));
            if (trade.getId() != null) {
                trade.update(decimal(item.closePrice()), longValue(item.accumulatedVolume()),
                        decimal(item.accumulatedTradeAmount()), longValue(item.foreignBuyQuantity()),
                        longValue(item.foreignSellQuantity()), longValue(item.foreignNetBuyQuantity()),
                        decimal(item.foreignBuyAmount()), decimal(item.foreignSellAmount()),
                        decimal(item.foreignNetBuyAmount()), longValue(item.personalBuyQuantity()),
                        longValue(item.personalSellQuantity()), longValue(item.personalNetBuyQuantity()),
                        decimal(item.personalBuyAmount()), decimal(item.personalSellAmount()),
                        decimal(item.personalNetBuyAmount()), longValue(item.institutionBuyQuantity()),
                        longValue(item.institutionSellQuantity()), longValue(item.institutionNetBuyQuantity()),
                        decimal(item.institutionBuyAmount()), decimal(item.institutionSellAmount()),
                        decimal(item.institutionNetBuyAmount()));
            }
            investorDailyTradeRepository.save(trade);
            saved = true;
        }
        return saved;
    }

    // 데이터가 최신데이터인지를 검사한다.
    static boolean isStale(LocalDateTime updatedAt, Duration freshness, LocalDateTime now) {
        return updatedAt == null || updatedAt.plus(freshness).isBefore(now);
    }

    // 투자자 수급 데이터가 캐싱시간을 초과했는지를 검사한다.
    private boolean isInvestorTradeStale(Stock stock, LocalDateTime updatedAt, LocalDateTime now) {
        if (StockMarketSchedules.isTradingTimeNow(stock)) {
            return isStale(updatedAt, Duration.ofSeconds(investorRefreshSeconds), now);
        }
        LocalDate expectedTradeDate = StockMarketSchedules.expectedLatestDailyPriceTradeDate(stock.getMarketType());
        LocalDateTime requiredFinalSnapshotAt = expectedTradeDate.atTime(
                StockMarketSchedules.getSchedule(stock.getMarketType()).regularCloseTime());
        return updatedAt == null || updatedAt.isBefore(requiredFinalSnapshotAt);
    }

    // 국내 종목 주식인지를 검사한다.
    private boolean isDomestic(Stock stock) {
        return stock != null && stock.getId() != null
                && (stock.getMarketType() == StockMarketType.KOSPI
                || stock.getMarketType() == StockMarketType.KOSDAQ);
    }

    // 특정 API 호출 하나가 예외가 발생해도, 시스템 전체 예외로 번지지 않기 위해 예외를 잡아서 예외로그를 기록한다.
    private void refreshSafely(Stock stock, String dataName, Runnable refreshAction) {
        try {
            refreshAction.run();
        } catch (RuntimeException e) {
            log.warn("국내 종목 상세 {} 갱신 실패. stockId={}, symbol={}",
                    dataName, stock.getId(), stock.getSymbol(), e);
        }
    }

    // 기준일자 Format 변경
    private LocalDate fiscalPeriod(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            return null;
        }
        try {
            return YearMonth.of(Integer.parseInt(digits.substring(0, 4)),
                    Integer.parseInt(digits.substring(4, 6))).atEndOfMonth();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate date(String value) {
        try {
            return KisValueParser.parseNullableDate(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(String value) {
        return KisValueParser.parseNullableBigDecimalOrNull(value);
    }

    private Long longValue(String value) {
        return KisValueParser.parseNullableLongOrNull(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
