package com.finmate.infra.kis.stock.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.core.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Component
@RequiredArgsConstructor
public class KisStockDailyPriceClient {
    private static final String DOMESTIC_DAILY_PRICE_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String OVERSEAS_DAILY_PRICE_PATH =
            "/uapi/overseas-price/v1/quotations/dailyprice";
    private static final String DOMESTIC_DAILY_PRICE_TR_ID = "FHKST03010100";
    private static final String OVERSEAS_DAILY_PRICE_TR_ID = "HHDFS76240000";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisRestClient kisRestClient;

    // 국내 일봉 조회 메서드
    public DomesticDailyPriceResponse fetchDomesticDailyPrices(String symbol,
                                                               LocalDate startDate,
                                                               LocalDate endDate,
                                                               boolean adjustedPrice) {
        // 입력 파라미터 검증
        validateRequired(symbol, "국내 종목코드는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");

        // api호출에 필요한 쿼리 파라미터 채우기
        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J"); // 종목시장 J:KRX
        params.put("FID_INPUT_ISCD", symbol); // 조회하고자 하는 종목
        params.put("FID_INPUT_DATE_1", formatDate(startDate)); // 조회 시작일자
        params.put("FID_INPUT_DATE_2", formatDate(endDate)); // 조회 종료일자
        params.put("FID_PERIOD_DIV_CODE", "D"); // 기간분류코드: 일봉
        // 국내 API는 0=수정주가, 1=원주가다.
        params.put("FID_ORG_ADJ_PRC", adjustedPrice ? "0" : "1");

        return kisRestClient.get(
                DOMESTIC_DAILY_PRICE_PATH,
                DOMESTIC_DAILY_PRICE_TR_ID,
                params,
                DomesticDailyPriceResponse.class);
    }

    // 해외 일봉 데이터를 조회하는 메서드
    public OverseasDailyPriceResponse fetchOverseasDailyPrices(String exchangeCode,
                                                               String symbol,
                                                               LocalDate baseDate,
                                                               boolean adjustedPrice) {
        // 입력 파라미터 검증
        validateRequired(exchangeCode, "해외 거래소코드는 필수입니다.");
        validateRequired(symbol, "해외 종목코드는 필수입니다.");

        // api호출에 필요한 쿼리 파라미터 채우기
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AUTH", ""); // 사용자 권한 정보(""로 설정)
        params.put("EXCD", exchangeCode); // 거래소 코드(NAS: 나스닥 ..)
        params.put("SYMB", symbol); // 종목코드(TSLA)
        params.put("GUBN", "0"); // 일/주/월 구분(0: 일, 1: 주, 2: 월)
        params.put("BYMD", formatNullableDate(baseDate)); // 조회기준일자
        // 해외 API는 0=수정주가 미반영, 1=수정주가 반영이다.
        params.put("MODP", adjustedPrice ? "1" : "0");

        return kisRestClient.get(
                OVERSEAS_DAILY_PRICE_PATH,
                OVERSEAS_DAILY_PRICE_TR_ID,
                params,
                OverseasDailyPriceResponse.class);
    }

    // LocalDate를 KIS API 요청 형식에 맞게 변환
    private String formatDate(LocalDate date) {
        return date.format(REQUEST_DATE_FORMATTER);
    }

    private String formatNullableDate(LocalDate date) {
        if (date == null) {
            return "";
        }

        return date.format(REQUEST_DATE_FORMATTER);
    }

    // 국내 일봉 API 전체 응답
    @JsonIgnoreProperties(ignoreUnknown = true) // JSON 응답에 record에 정의하지 않은 필드가 있어도 무시.
    public record DomesticDailyPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output1") DomesticDailyPriceSummary output1, // 종목 요약 정보
            @JsonProperty("output2") List<DomesticDailyPriceItem> output2 // 날짜별 일봉 데이터 목록
    ) implements KisApiResponse {
    }

    // 국내 종목 요약 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticDailyPriceSummary(
            @JsonProperty("hts_kor_isnm") String nameKo, // 한글 종목명
            @JsonProperty("stck_shrn_iscd") String symbol, // 주식 단축 종목코드
            @JsonProperty("stck_prpr") String currentPrice // 주식 현재가
    ) {
    }

    // 국내 일별 시세 데이터 하나
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticDailyPriceItem(
            @JsonProperty("stck_bsop_date") String tradeDate, // 주식 거래일자
            @JsonProperty("stck_oprc") String openPrice, // 시가
            @JsonProperty("stck_hgpr") String highPrice, // 고가
            @JsonProperty("stck_lwpr") String lowPrice, // 저가
            @JsonProperty("stck_clpr") String closePrice, // 종가
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 누적 거래대금
            @JsonProperty("mod_yn") String modified // 수정주가 여부
    ) {
    }

    // 해외 일봉 API 전체 응답
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasDailyPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output1") OverseasDailyPriceSummary output1, // 해외 종목 요약 정보
            @JsonProperty("output2") List<OverseasDailyPriceItem> output2 // 해외 일봉 데이터 목록
    ) implements KisApiResponse {
    }

    // 해외 종목 요약 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasDailyPriceSummary(
            @JsonProperty("rsym") String realtimeSymbol, // 실시간 조회용 종목 심볼
            @JsonProperty("zdiv") String decimalPointPosition // 소수점 위치를 나타내는 필드
    ) {
    }

    // 해외 일별 시세 데이터 하나
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasDailyPriceItem(
            @JsonProperty("xymd") String tradeDate, // 주식 거래일자
            @JsonProperty("open") String openPrice, // 시가
            @JsonProperty("high") String highPrice, // 고가
            @JsonProperty("low") String lowPrice, // 저가
            @JsonProperty("clos") String closePrice, // 종가
            @JsonProperty("tvol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("tamt") String accumulatedTradeAmount // 누적 거래대금
    ) {
    }
}
