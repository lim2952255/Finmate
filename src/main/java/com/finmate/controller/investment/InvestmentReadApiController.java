package com.finmate.controller.investment;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorType;
import com.finmate.domain.market.dto.MarketDataChartPeriod;
import com.finmate.domain.market.dto.MarketIndicatorPageInfo;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.domain.stock.dto.detail.StockChartCandleData;
import com.finmate.domain.stock.dto.detail.DomesticStockDetailInfo;
import com.finmate.domain.stock.dto.detail.StockChartInterval;
import com.finmate.domain.stock.dto.detail.StockChartPriceSummary;
import com.finmate.domain.stock.dto.detail.StockDetailPageInfo;
import com.finmate.domain.stock.dto.detail.StockMetadataDisplayInfo;
import com.finmate.domain.stock.dto.trading.StockPortfolioPageInfo;
import com.finmate.domain.stock.dto.trading.StockPortfolioIndustryAllocation;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.market.MarketDataService;
import com.finmate.service.market.MarketRealtimeQuoteService;
import com.finmate.service.stock.StockDetailService;
import com.finmate.service.stock.trading.StockTradingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/investment-read")
public class InvestmentReadApiController {
    private final StockTradingQueryService tradingQueryService;
    private final StockDetailService stockDetailService;
    private final MarketDataService marketDataService;
    private final MarketRealtimeQuoteService marketRealtimeQuoteService;

    private static final Set<StockConceptCode> STOCK_DETAIL_CONCEPTS = Set.of(
            StockConceptCode.DAILY_CANDLE_CHART,
            StockConceptCode.PER,
            StockConceptCode.PBR,
            StockConceptCode.EPS,
            StockConceptCode.BPS,
            StockConceptCode.MARKET_CAP,
            StockConceptCode.LISTED_SHARES,
            StockConceptCode.FOREIGN_HOLDING_QUANTITY,
            StockConceptCode.FOREIGN_EXHAUSTION_RATE,
            StockConceptCode.VALUATION_AND_PROFITABILITY,
            StockConceptCode.FINANCIAL_ANALYSIS_METRICS,
            StockConceptCode.REVENUE,
            StockConceptCode.OPERATING_PROFIT,
            StockConceptCode.NET_INCOME,
            StockConceptCode.FINANCIAL_RATIOS,
            StockConceptCode.INCOME_STATEMENT,
            StockConceptCode.BALANCE_SHEET,
            StockConceptCode.INVESTOR_TRADING_FLOW,
            StockConceptCode.SHORT_SELLING_AND_SECURITIES_LENDING);

    @GetMapping("/portfolio")
    public PortfolioResponse portfolio(@RequestParam(required = false) Long investmentId,
                                       @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        StockPortfolioPageInfo info = tradingQueryService.getPortfolioPageInfo(principal.getId(), investmentId);
        ExchangeRateResponse exchangeRate = findUsdKrwExchangeRate();
        return new PortfolioResponse(info.getInvestments().stream().map(AccountResponse::from).toList(),
                info.getSelectedInvestment() == null ? null : info.getSelectedInvestment().getId(), info.isAllAccounts(),
                info.getCurrencies().stream().map(CurrencyResponse::from).toList(),
                info.getIndustryAllocations().stream().map(IndustryAllocationResponse::from).toList(),
                exchangeRate,
                info.getHoldings().stream().map(holding -> HoldingResponse.from(holding, info)).toList());
    }

	// React에서 차트간격을 파라미터로 넘겨주면, 이에 해당하는 기간봉 데이터를 내려준다.
    @GetMapping("/stock-detail")
    public StockDetailResponse stockDetail(@RequestParam Long stockId,
                                           @RequestParam(defaultValue = "DAY") StockChartInterval interval,
                                           @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        StockDetailPageInfo info = stockDetailService.getStockDetailPageInfo(stockId, interval);
        Stock stock = info.getStock();
        return new StockDetailResponse(stock.getId(), stock.getSymbol(), stock.getNameKo(), stock.getNameEn(),
                stock.getMarketType().name(), stock.getSecurityType().name(), stock.getCurrency(), stock.isTradable(),
                principal.getId(), principal.getDisplayName(),
                info.getLatestTradeDate(), info.getLatestCandleAt(),
                info.getLatestClosePrice() == null ? null : info.getLatestClosePrice().toPlainString(),
                info.getLatestChangeAmount() == null ? null : info.getLatestChangeAmount().toPlainString(),
                info.getLatestChangeRate() == null ? null : info.getLatestChangeRate().toPlainString(),
                info.getCurrencySymbol(), info.getPriceDecimalDigits(), info.getChartPriceSummary(),
                info.getSelectedInterval().name(), Arrays.stream(info.getChartIntervals()).map(value -> new Option(value.name(), value.getDisplayName())).toList(),
                info.getCurrentCandleTradeDate(),
                info.getCurrentCandleBaseVolume() == null ? null : info.getCurrentCandleBaseVolume().toString(),
                info.getCurrentCandleBaseTradeAmount() == null ? null : info.getCurrentCandleBaseTradeAmount().toPlainString(),
                Arrays.stream(StockConceptCode.values())
                        .filter(STOCK_DETAIL_CONCEPTS::contains)
                        .map(value -> new Option(value.name(), conceptLabel(value)))
                        .toList(),
                info.getChartCandles(), info.getMetadataDisplayInfo(), info.getDomesticDetailInfo(),
                marketSessions(stock),
                info.isStockTradingAvailable(), info.getStockTradingTimeDescription());
    }

    @GetMapping("/market")
    public MarketResponse market(@RequestParam MarketIndicatorType type,
                                 @RequestParam(required = false) MarketIndicatorSymbol indicator,
                                 @RequestParam(defaultValue = "ONE_YEAR") MarketDataChartPeriod period) {
        MarketIndicatorPageInfo info = marketDataService.getMarketIndicatorPageInfo(type, indicator, period);
        return new MarketResponse(info.selectedIndicator().name(), info.selectedIndicator().getDisplayName(),
                info.selectedIndicator().getNameKo(), info.selectedIndicator().getDescription(),
                info.selectedIndicator().getUnit(), info.selectedIndicator().getFractionDigits(),
                info.selectedIndicator().getRealtimeMode(), info.savedDailyPriceCount(),
                info.indicators().stream().map(value -> new Option(value.name(), value.getDisplayName())).toList(), period.name(), Arrays.stream(MarketDataChartPeriod.values()).map(value -> new Option(value.name(), value.getDisplayName())).toList(),
                info.dailyPrices().stream().map(value -> new MarketPrice(value.getTradeDate().toString(),
                        value.getOpenPrice().toPlainString(), value.getHighPrice().toPlainString(),
                        value.getLowPrice().toPlainString(), value.getClosePrice().toPlainString(),
                        value.getAccumulatedVolume() == null ? null : value.getAccumulatedVolume().toString())).toList());
    }

    public record Option(String value, String label) {}
    public record AccountResponse(Long id, String label) { static AccountResponse from(Investment value) { return new AccountResponse(value.getId(), value.getSecuritiesCompanyCode().getDisplayName() + " " + value.getAccountNumber()); } }
    public record PortfolioResponse(List<AccountResponse> accounts, Long selectedInvestmentId, boolean allAccounts,
                                    List<CurrencyResponse> currencies,
                                    List<IndustryAllocationResponse> industryAllocations,
                                    ExchangeRateResponse usdKrwExchangeRate,
                                    List<HoldingResponse> holdings) {}
    public record CurrencyResponse(String code, String displayName, int fractionDigits) {
        static CurrencyResponse from(CurrencyCode value) {
            return new CurrencyResponse(value.name(), value.getDisplayName(), value.getFractionDigits());
        }
    }
    public record IndustryAllocationResponse(String currency, String groupName, String industryName,
                                             String purchaseAmount, String percentage) {
        static IndustryAllocationResponse from(StockPortfolioIndustryAllocation value) {
            return new IndustryAllocationResponse(value.currencyCode().name(), value.groupName(), value.industryName(),
                    value.purchaseAmount().toPlainString(), value.percentage().toPlainString());
        }
    }
    public record ExchangeRateResponse(String price, String receivedAt) {}
    public record HoldingResponse(Long id, Long investmentId, String securitiesCompany, String accountNumber,
                                  Long stockId, String stockName, String symbol, String market, String currency,
                                  int fractionDigits, String quantity, String availableQuantity, String averagePrice,
                                  String industry, String industryGroup, String valuationPrice, String valuationPriceSource,
                                  boolean tradingAvailable, String tradingTimeDescription) {
        static HoldingResponse from(StockHolding value, StockPortfolioPageInfo info) {
            Long stockId = value.getStock().getId();
            return new HoldingResponse(value.getId(), value.getInvestment().getId(),
                    value.getInvestment().getSecuritiesCompanyCode().getDisplayName(), value.getInvestment().getAccountNumber(),
                    stockId, value.getStock().getNameKo(), value.getStock().getSymbol(), value.getStock().getMarketType().name(),
                    value.getCurrencyCode().name(), value.getCurrencyCode().getFractionDigits(),
                    value.getQuantity().toPlainString(), value.getAvailableQuantity().toPlainString(),
                    value.getAveragePurchasePrice().toPlainString(), info.getIndustryName(stockId), info.getIndustryGroupName(stockId),
                    info.getValuationPrice(stockId) == null ? null : info.getValuationPrice(stockId).toPlainString(),
                    info.getValuationPriceSource(stockId), info.isStockTradingAvailable(value.getStock()),
                    info.getStockTradingTimeDescription(value.getStock()));
        }
    }
    public record StockDetailResponse(Long id, String symbol, String nameKo, String nameEn,
                                      String market, String securityType, String currency, boolean tradable,
                                      Long currentUserId, String currentUsername,
                                      java.time.LocalDate latestTradeDate, String latestCandleAt, String latestClosePrice,
                                      String latestChangeAmount, String latestChangeRate,
                                      String currencySymbol, int priceDecimalDigits,
                                      StockChartPriceSummary chartPriceSummary,
                                      String selectedInterval, List<Option> intervals,
                                      java.time.LocalDate currentCandleTradeDate,
                                      String currentCandleBaseVolume, String currentCandleBaseTradeAmount,
                                      List<Option> concepts, List<StockChartCandleData> candles,
                                      StockMetadataDisplayInfo metadataDisplayInfo,
                                      DomesticStockDetailInfo domesticDetailInfo,
                                      List<MarketSessionResponse> marketSessions,
                                      boolean tradingAvailable, String tradingTimeDescription) {}
    public record MarketSessionResponse(String market, boolean open, String status) {}
    public record MarketResponse(String indicator, String displayName, String nameKo, String description,
                                 String unit, int fractionDigits, String realtimeMode, int savedDailyPriceCount,
                                 List<Option> indicators, String period, List<Option> periods,
                                 List<MarketPrice> prices) {}
    public record MarketPrice(String date, String open, String high, String low, String close, String volume) {}

    private ExchangeRateResponse findUsdKrwExchangeRate() {
        try {
            return marketRealtimeQuoteService.getLatest(MarketIndicatorSymbol.USD_KRW)
                    .filter(message -> message.currentPrice() != null && message.currentPrice().signum() > 0)
                    .map(message -> new ExchangeRateResponse(
                            message.currentPrice().toPlainString(),
                            message.receivedAt() == null ? null : message.receivedAt().toString()))
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.warn("포트폴리오 환산용 USD/KRW 환율을 조회하지 못했습니다.", exception);
            return null;
        }
    }

    private List<MarketSessionResponse> marketSessions(Stock stock) {
        ZonedDateTime now = ZonedDateTime.now();
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            boolean open = StockMarketSchedules.isMarketTradingTime(stock.getMarketType(), now);
            return List.of(new MarketSessionResponse("NASDAQ", open, open ? "장중" : "마감"));
        }

        boolean krxOpen = StockMarketSchedules.isMarketTradingTime(stock.getMarketType(), now);
        Integer nxtPermissionCode = stock.getNxtTradingPermissionCode();
        MarketSessionResponse nxtStatus;
        if (nxtPermissionCode == null) {
            nxtStatus = new MarketSessionResponse("NXT", false, "비대상");
        } else if (nxtPermissionCode == 0) {
            nxtStatus = new MarketSessionResponse("NXT", false, "거래 제한");
        } else {
            boolean nxtOpen = StockMarketSchedules.isNxtTradingTime(stock, now);
            nxtStatus = new MarketSessionResponse("NXT", nxtOpen, nxtOpen ? "장중" : "마감");
        }

        return List.of(
                new MarketSessionResponse("KRX", krxOpen, krxOpen ? "장중" : "마감"),
                nxtStatus);
    }

    private static String conceptLabel(StockConceptCode code) {
        return switch (code) {
            case DAILY_CANDLE_CHART -> "일봉 차트 보는 법";
            case PER -> "PER · 주가수익비율";
            case PBR -> "PBR · 주가순자산비율";
            case EPS -> "EPS · 주당순이익";
            case BPS -> "BPS · 주당순자산";
            case MARKET_CAP -> "시가총액";
            case LISTED_SHARES -> "상장 주식수";
            case FOREIGN_HOLDING_QUANTITY -> "외국인 보유수량";
            case FOREIGN_EXHAUSTION_RATE -> "외국인 소진율";
            case VALUATION_AND_PROFITABILITY -> "수익성과 기업가치";
            case FINANCIAL_ANALYSIS_METRICS -> "재무 분석 기준";
            case REVENUE -> "매출액 분석";
            case OPERATING_PROFIT -> "영업이익 분석";
            case NET_INCOME -> "당기순이익 분석";
            case FINANCIAL_RATIOS -> "재무비율 분석";
            case INCOME_STATEMENT -> "손익계산서 분석";
            case BALANCE_SHEET -> "대차대조표 분석";
            case INVESTOR_TRADING_FLOW -> "투자자별 매매동향";
            case SHORT_SELLING_AND_SECURITIES_LENDING -> "공매도와 대차잔고";
            default -> code.name();
        };
    }
}
