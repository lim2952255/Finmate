package com.finmate.infra.kis.exchange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 해외 지수와 환율 기간별 시세를 조회하는 KIS REST 클라이언트
@Component
@RequiredArgsConstructor
public class KisOverseasMarketDailyChartPriceClient {
    // 해외 지수 및 환율 시세를 조회하는 경로 및 TR ID
    private static final String DAILY_CHART_PRICE_PATH =
            "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice";
    private static final String DAILY_CHART_PRICE_TR_ID = "FHKST03030100";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisRestClient kisRestClient;

    // KIS API를 호출하여 해외 지수 시세 및 환율 시세를 조회하는 메서드
    public DailyChartPriceResponse fetchDailyChartPrices(String marketDivisionCode,
                                                         String symbol,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         String periodDivisionCode) {
        validateRequired(marketDivisionCode, "시장 분류 코드는 필수입니다.");
        validateRequired(symbol, "지수/환율 코드는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");
        validateRequired(periodDivisionCode, "기간 분류 코드는 필수입니다.");

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException("조회 시작일자는 종료일자보다 늦을 수 없습니다.");
        }

        // API 요청에 필요한 파라미터 정보들을 설정
        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", marketDivisionCode); // 시장 분류 코드: 해외 지수/환율 구분
        params.put("FID_INPUT_ISCD", symbol); // 어떤 지수 / 환율을 조회할지 결정
        params.put("FID_INPUT_DATE_1", formatDate(startDate)); // 조회 시작일자
        params.put("FID_INPUT_DATE_2", formatDate(endDate)); // 조회 종료 일자
        params.put("FID_PERIOD_DIV_CODE", periodDivisionCode); // 기간 구분 (D: 일봉, W: 주봉, M: 월봉 Y: 년봉)

        return kisRestClient.get(
                DAILY_CHART_PRICE_PATH,
                DAILY_CHART_PRICE_TR_ID,
                params,
                DailyChartPriceResponse.class);
    }

    private String formatDate(LocalDate date) {
        return date.format(REQUEST_DATE_FORMATTER);
    }

    // 응답 전체 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyChartPriceResponse(
            @JsonProperty("rt_cd") String rtCd, // 응답 성공 / 실패 코드
            @JsonProperty("msg_cd") String msgCd, // 메세지 코드
            @JsonProperty("msg1") String msg1, // 응답 메세지
            @JsonProperty("output1") DailyChartPriceSummary output1, // 요약정보(현재가, 전일대, 등락률 등)
            @JsonProperty("output2") List<DailyChartPriceItem> output2 // 기간별 시세 목록(일/주봉/월봉 데이터 리스트)
    ) implements KisApiResponse {
    }

    // 요약정보를 담은 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyChartPriceSummary(
            @JsonProperty("hts_kor_isnm") String nameKo, // 한글명
            @JsonProperty("stck_shrn_iscd") String symbol, // 지수(환율) 코드
            @JsonProperty("ovrs_nmix_prpr") String currentPrice, // 현재가
            @JsonProperty("ovrs_nmix_prdy_vrss") String changeAmount, // 전일대비 변화량
            @JsonProperty("prdy_vrss_sign") String changeSign, // 전일 대비 부호
            @JsonProperty("prdy_ctrt") String changeRate, // 전일 대비 등락률
            @JsonProperty("ovrs_nmix_prdy_clpr") String previousClosePrice, // 전일 종가
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("prdy_vol") String previousVolume, // 전일 거래량
            @JsonProperty("ovrs_prod_oprc") String openPrice, // 시가
            @JsonProperty("ovrs_prod_hgpr") String highPrice, // 고가
            @JsonProperty("ovrs_prod_lwpr") String lowPrice // 저가
    ) {
    }

    // 기간별 상세 시세정보 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyChartPriceItem(
            @JsonProperty("stck_bsop_date") String tradeDate, // 거래일
            @JsonProperty("ovrs_nmix_oprc") String openPrice, // 시가
            @JsonProperty("ovrs_nmix_hgpr") String highPrice, // 고가
            @JsonProperty("ovrs_nmix_lwpr") String lowPrice, // 저가
            @JsonProperty("ovrs_nmix_prpr") String closePrice, // 종가
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("mod_yn") String modified // 수정 여부
    ) {
    }
}
