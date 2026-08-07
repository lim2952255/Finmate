package com.finmate.infra.kis.stock.detail;

import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 종목 상세정보 API를 호출하는 클라이언트
@Component
@RequiredArgsConstructor
public class KisDomesticStockDetailClient {
    private static final String MARKET_CODE = "J";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String CURRENT_PRICE_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String FINANCIAL_RATIO_PATH =
            "/uapi/domestic-stock/v1/finance/financial-ratio";
    private static final String INCOME_STATEMENT_PATH =
            "/uapi/domestic-stock/v1/finance/income-statement";
    private static final String BALANCE_SHEET_PATH =
            "/uapi/domestic-stock/v1/finance/balance-sheet";
    private static final String INVESTOR_TRADE_PATH =
            "/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily";

    // 식별 코드
    private static final String CURRENT_PRICE_TR_ID = "FHKST01010100";
    private static final String FINANCIAL_RATIO_TR_ID = "FHKST66430300";
    private static final String INCOME_STATEMENT_TR_ID = "FHKST66430200";
    private static final String BALANCE_SHEET_TR_ID = "FHKST66430100";
    private static final String INVESTOR_TRADE_TR_ID = "FHPTJ04160001";

    // 실제 API 요청을 전송하고 응답을 받는 클라이언트
    private final KisRestClient kisRestClient;

    // 국내 종목 현재가 조회
    public KisCurrentPriceResponse fetchCurrentPrice(String symbol) {
        validateSymbol(symbol);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", MARKET_CODE);
        params.put("FID_INPUT_ISCD", symbol);

        return kisRestClient.get(
                CURRENT_PRICE_PATH,
                CURRENT_PRICE_TR_ID,
                params,
                KisCurrentPriceResponse.class);
    }

    // 국내 종목 재무비율 조회
    public KisFinancialRatioResponse fetchFinancialRatios(String symbol, FinancialPeriod period) {
        validateSymbolAndPeriod(symbol, period);

        return kisRestClient.get(
                FINANCIAL_RATIO_PATH,
                FINANCIAL_RATIO_TR_ID,
                financialParams(symbol, period),
                KisFinancialRatioResponse.class);
    }

    // 국내 종목 손익계산서 조회
    public KisIncomeStatementResponse fetchIncomeStatements(String symbol, FinancialPeriod period) {
        validateSymbolAndPeriod(symbol, period);

        return kisRestClient.get(
                INCOME_STATEMENT_PATH,
                INCOME_STATEMENT_TR_ID,
                financialParams(symbol, period),
                KisIncomeStatementResponse.class);
    }

    // 국내 종목 대차대조표 조회
    public KisBalanceSheetResponse fetchBalanceSheets(String symbol, FinancialPeriod period) {
        validateSymbolAndPeriod(symbol, period);

        return kisRestClient.get(
                BALANCE_SHEET_PATH,
                BALANCE_SHEET_TR_ID,
                financialParams(symbol, period),
                KisBalanceSheetResponse.class);
    }

    // 국내 종목 투자자 매매동향 조회
    public KisInvestorTradeResponse fetchDailyInvestorTrades(String symbol, LocalDate baseDate) {
        validateSymbol(symbol);
        validateRequired(baseDate, "투자자 매매동향 조회 기준일자는 필수입니다.");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", MARKET_CODE);
        params.put("FID_INPUT_ISCD", symbol);
        params.put("FID_INPUT_DATE_1", baseDate.format(REQUEST_DATE_FORMATTER));
        params.put("FID_ORG_ADJ_PRC", "");
        params.put("FID_ETC_CLS_CODE", "");

        return kisRestClient.get(
                INVESTOR_TRADE_PATH,
                INVESTOR_TRADE_TR_ID,
                params,
                KisInvestorTradeResponse.class);
    }

    private Map<String, String> financialParams(String symbol, FinancialPeriod period) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_DIV_CLS_CODE", period.kisCode());
        params.put("fid_cond_mrkt_div_code", MARKET_CODE);
        params.put("fid_input_iscd", symbol);
        return params;
    }

    private void validateSymbolAndPeriod(String symbol, FinancialPeriod period) {
        validateSymbol(symbol);
        validateRequired(period, "재무 조회 기간 구분은 필수입니다.");
    }

    private void validateSymbol(String symbol) {
        validateRequired(symbol, "국내 종목코드는 필수입니다.");
    }
}
