package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.metadata.domestic.DomesticStockDetailRefreshState;
import com.finmate.domain.stock.metadata.domestic.DomesticStockInvestorDailyTrade;
import com.finmate.domain.stock.metadata.domestic.DomesticStockLoanTransactionDaily;
import com.finmate.domain.stock.metadata.domestic.DomesticStockShortSaleDaily;
import com.finmate.infra.kis.parser.KisValueParser;
import com.finmate.infra.kis.stock.detail.KisDailyLoanTransactionResponse;
import com.finmate.infra.kis.stock.detail.KisDailyShortSaleResponse;
import com.finmate.infra.kis.stock.detail.KisDomesticStockDetailClient;
import com.finmate.infra.kis.stock.detail.KisInvestorTradeResponse;
import com.finmate.repository.stock.metadata.domestic.DomesticStockDetailRefreshStateRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockInvestorDailyTradeRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockLoanTransactionDailyRepository;
import com.finmate.repository.stock.metadata.domestic.DomesticStockShortSaleDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 마감된 일별 투자자 수급, 공매도, 대차 데이터를 DB에 동기화한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockFinalizedDailyFlowSyncService {
    private static final String KRX_MARKET_CODE = "J";
    private static final int INITIAL_HISTORY_MONTHS = 3;

    private final KisDomesticStockDetailClient kisClient;
    private final DomesticStockInvestorDailyTradeRepository investorRepository;
    private final DomesticStockShortSaleDailyRepository shortSaleRepository;
    private final DomesticStockLoanTransactionDailyRepository loanRepository;
    private final DomesticStockDetailRefreshStateRepository refreshStateRepository;

    public void synchronize(Stock stock,
                            DomesticStockDetailRefreshState state,
                            LocalDate finalizedDate) {
        synchronizeInvestor(stock, state, finalizedDate); // 투자자 수급 동기화
        synchronizeShortSale(stock, state, finalizedDate); // 공매도 데이터 동기화
        synchronizeLoan(stock, state, finalizedDate); // 대차거래 데이터 동기화
    }

    private void synchronizeInvestor(Stock stock,
                                     DomesticStockDetailRefreshState state,
                                     LocalDate finalizedDate) {
        LocalDate startDate = investorRepository
                .findTopByStock_IdAndMarketCodeOrderByTradeDateDesc(stock.getId(), KRX_MARKET_CODE)
                .map(DomesticStockInvestorDailyTrade::getTradeDate)
                .map(date -> date.plusDays(1))
                .orElse(null);
        if (startDate != null && startDate.isAfter(finalizedDate)) {
            return;
        }

        // 이 API는 종료일을 별도로 받지 않으므로 확정일 기준 응답에서 누락 구간만 골라 저장한다.
        refreshSafely(stock, "확정 투자자 매매동향", () -> {
            KisInvestorTradeResponse response = kisClient.fetchDailyInvestorTrades(
                    stock.getSymbol(), finalizedDate);
            if (saveInvestorTrades(stock, response, startDate, finalizedDate)) {
                state.markInvestorTradeUpdated(LocalDateTime.now());
                refreshStateRepository.save(state);
            }
        });
    }

    private void synchronizeShortSale(Stock stock,
                                      DomesticStockDetailRefreshState state,
                                      LocalDate finalizedDate) {
        // DB에 저장되어있는 최신 데이터의 날짜를 조회하고, 이를 finalizedDate와 비교한다.
        // 만약 최신 데이터의 날짜가 finalizedDate와 동일하면 동기화 필요 x
        // 차이가 난다면 해당 차이만큼의 데이터를 조회해서 DB에 동기화해야한다. (온디멘드 방식)
        LocalDate startDate = shortSaleRepository.findTopByStock_IdOrderByTradeDateDesc(stock.getId())
                .map(DomesticStockShortSaleDaily::getTradeDate)
                .map(date -> date.plusDays(1))
                .orElse(finalizedDate.minusMonths(INITIAL_HISTORY_MONTHS));
        if (startDate.isAfter(finalizedDate)) {
            return;
        }
        // 온디멘드방식으로 부족한 데이터를 조회하고 DB에 저장한다.
        refreshSafely(stock, "확정 공매도 거래", () -> {
            KisDailyShortSaleResponse response = kisClient.fetchDailyShortSales(
                    stock.getSymbol(), startDate, finalizedDate);
            if (saveShortSales(stock, response, startDate, finalizedDate)) {
                state.markShortSaleUpdated(LocalDateTime.now());
                refreshStateRepository.save(state);
            }
        });
    }

    private void synchronizeLoan(Stock stock,
                                 DomesticStockDetailRefreshState state,
                                 LocalDate finalizedDate) {
        // DB에 저장되어있는 최신 데이터의 날짜를 조회하고, 이를 finalizedDate와 비교한다.
        // 만약 최신 데이터의 날짜가 finalizedDate와 동일하면 동기화 필요 x
        // 차이가 난다면 해당 차이만큼의 데이터를 조회해서 DB에 동기화해야한다. (온디멘드 방식)
        LocalDate startDate = loanRepository.findTopByStock_IdOrderByTradeDateDesc(stock.getId())
                .filter(this::hasLoanTransactionValues)
                .map(DomesticStockLoanTransactionDaily::getTradeDate)
                .map(date -> date.plusDays(1))
                .orElse(finalizedDate.minusMonths(INITIAL_HISTORY_MONTHS));
        if (startDate.isAfter(finalizedDate)) {
            return;
        }
        // 온디멘드방식으로 부족한 데이터를 DB에 동기화한다
        refreshSafely(stock, "확정 대차 거래", () -> {
            KisDailyLoanTransactionResponse response = kisClient.fetchDailyLoanTransactions(
                    stock.getSymbol(), startDate, finalizedDate);
            if (saveLoanTransactions(stock, response, startDate, finalizedDate)) {
                state.markLoanTransactionUpdated(LocalDateTime.now());
                refreshStateRepository.save(state);
            }
        });
    }

    private boolean saveInvestorTrades(Stock stock,
                                       KisInvestorTradeResponse response,
                                       LocalDate startDate,
                                       LocalDate finalizedDate) {
        List<DomesticStockInvestorDailyTrade> trades = new ArrayList<>();
        for (KisInvestorTradeResponse.DailyInvestorTrade item
                : safeList(response == null ? null : response.output2())) {
            LocalDate tradeDate = date(item.tradeDate());
            if (tradeDate == null
                    || (startDate != null && tradeDate.isBefore(startDate))
                    || tradeDate.isAfter(finalizedDate)) {
                continue;
            }
            trades.add(DomesticStockInvestorDailyTrade.create(
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
        }
        investorRepository.saveAll(trades);
        return !trades.isEmpty();
    }

    private boolean saveShortSales(Stock stock,
                                   KisDailyShortSaleResponse response,
                                   LocalDate startDate,
                                   LocalDate finalizedDate) {
        List<DomesticStockShortSaleDaily> shortSales = new ArrayList<>();
        for (KisDailyShortSaleResponse.DailyShortSale item
                : safeList(response == null ? null : response.output2())) {
            LocalDate tradeDate = date(item.tradeDate());
            if (tradeDate == null || tradeDate.isBefore(startDate) || tradeDate.isAfter(finalizedDate)) {
                continue;
            }
            shortSales.add(DomesticStockShortSaleDaily.create(stock, tradeDate,
                    decimal(item.closePrice()), longValue(item.accumulatedVolume()),
                    longValue(item.shortSaleQuantity()), decimal(item.shortSaleVolumeRatio()),
                    decimal(item.accumulatedTradeAmount()), decimal(item.shortSaleTradeAmount()),
                    decimal(item.shortSaleTradeAmountRatio()), decimal(item.averagePrice())));
        }
        shortSaleRepository.saveAll(shortSales);
        return !shortSales.isEmpty();
    }

    private boolean saveLoanTransactions(Stock stock,
                                         KisDailyLoanTransactionResponse response,
                                         LocalDate startDate,
                                         LocalDate finalizedDate) {
        boolean saved = false;
        for (Map<String, String> item : response == null ? List.<Map<String, String>>of() : response.rows()) {
            LocalDate tradeDate = date(firstValue(item, "stck_bsop_date", "bsop_date", "deal_date"));
            if (tradeDate == null || tradeDate.isBefore(startDate) || tradeDate.isAfter(finalizedDate)) {
                continue;
            }
            BigDecimal closePrice = decimal(firstValue(item, "stck_prpr", "stck_clpr", "close_price"));
            Long newQuantity = longValue(firstValue(item, "new_stcn", "new_stln_qty", "stln_new_qty"));
            Long redeemedQuantity = longValue(firstValue(item, "rdmp_stcn", "rdmp_stln_qty",
                    "stln_rdmn_qty", "stln_repay_qty"));
            Long balanceQuantity = longValue(firstValue(item, "rmnd_stcn", "stln_blce_qty",
                    "loan_balance_qty"));
            BigDecimal newAmount = decimal(firstValue(item, "new_stln_amt", "stln_new_amt"));
            BigDecimal redeemedAmount = decimal(firstValue(item, "rdmp_stln_amt", "stln_rdmn_amt",
                    "stln_repay_amt"));
            BigDecimal balanceAmount = decimal(firstValue(item, "rmnd_amt", "stln_blce_amt",
                    "loan_balance_amt"));
            DomesticStockLoanTransactionDaily daily = loanRepository
                    .findByStock_IdAndTradeDate(stock.getId(), tradeDate)
                    .orElseGet(() -> DomesticStockLoanTransactionDaily.create(stock, tradeDate, closePrice,
                            newQuantity, redeemedQuantity, balanceQuantity, newAmount, redeemedAmount,
                            balanceAmount));
            if (daily.getId() != null) {
                daily.update(closePrice, newQuantity, redeemedQuantity, balanceQuantity, newAmount,
                        redeemedAmount, balanceAmount);
            }
            loanRepository.save(daily);
            saved = true;
        }
        return saved;
    }

    private boolean hasLoanTransactionValues(DomesticStockLoanTransactionDaily daily) {
        return daily.getNewLoanQuantity() != null
                || daily.getRedeemedLoanQuantity() != null
                || daily.getLoanBalanceQuantity() != null;
    }

    private void refreshSafely(Stock stock, String dataName, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("국내 종목 상세 {} 갱신 실패. stockId={}, symbol={}",
                    dataName, stock.getId(), stock.getSymbol(), e);
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

    private String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
