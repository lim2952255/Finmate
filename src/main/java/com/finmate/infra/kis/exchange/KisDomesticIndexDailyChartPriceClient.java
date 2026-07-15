package com.finmate.infra.kis.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
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

// 코스피/코스닥 과 같은 국내 지수 기간별 시세를 조회하는 KIS REST 클라이언트
@Component
@RequiredArgsConstructor
public class KisDomesticIndexDailyChartPriceClient {
    private static final String DAILY_INDEX_CHART_PRICE_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice";
    private static final String DAILY_INDEX_CHART_PRICE_TR_ID = "FHKUP03500100";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisRestClient kisRestClient;

    // KIS API를 호출하여 국내 지수 기간별 시세를 조회하는 메서드
    public DailyIndexChartPriceResponse fetchDailyIndexChartPrices(String marketDivisionCode,
                                                                   String indexCode,
                                                                   LocalDate startDate,
                                                                   LocalDate endDate,
                                                                   String periodDivisionCode) {
        validateRequired(marketDivisionCode, "시장 분류 코드는 필수입니다.");
        validateRequired(indexCode, "국내 지수 코드는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");
        validateRequired(periodDivisionCode, "기간 분류 코드는 필수입니다.");

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException("조회 시작일자는 종료일자보다 늦을 수 없습니다.");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", marketDivisionCode); // 업종: U
        params.put("FID_INPUT_ISCD", indexCode); // 0001: KOSPI, 1001: KOSDAQ
        params.put("FID_INPUT_DATE_1", formatDate(startDate)); // 조회 시작일자
        params.put("FID_INPUT_DATE_2", formatDate(endDate)); // 조회 종료일자
        params.put("FID_PERIOD_DIV_CODE", periodDivisionCode); // 기간 구분 (D: 일봉, W: 주봉, M: 월봉 Y: 년봉)

        // KisRestClient를 활용해서 KIS API에서 일봉 데이터를 받아온다.
        return kisRestClient.get(
                DAILY_INDEX_CHART_PRICE_PATH,
                DAILY_INDEX_CHART_PRICE_TR_ID,
                params,
                DailyIndexChartPriceResponse.class);
    }

    private String formatDate(LocalDate date) {
        return date.format(REQUEST_DATE_FORMATTER);
    }

    // 국내 지수 기간별 시세 전체 응답 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyIndexChartPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output1") DailyIndexChartPriceSummary output1,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            @JsonProperty("output2") List<DailyIndexChartPriceItem> output2
    ) implements KisApiResponse {
    }

    // 국내 지수 요약 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyIndexChartPriceSummary(
            @JsonProperty("hts_kor_isnm") String nameKo, // HTS 한글 종목명
            @JsonProperty("bstp_nmix_prpr") String currentPrice, // 업종 지수 현재가
            @JsonProperty("bstp_nmix_prdy_vrss") String changeAmount, // 업종 지수 전일 대비
            @JsonProperty("prdy_vrss_sign") String changeSign, // 전일 대비 부호
            @JsonProperty("bstp_nmix_prdy_ctrt") String changeRate, // 업종 지수 전일 대비율
            @JsonProperty("prdy_nmix") String previousClosePrice, // 전일 지수
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 누적 거래 대금
            @JsonProperty("bstp_cls_code") String indexClassCode, // 업종 구분 코드
            @JsonProperty("prdy_vol") String previousVolume, // 전일 거래량
            @JsonProperty("bstp_nmix_oprc") String openPrice, // 업종 지수 시가
            @JsonProperty("bstp_nmix_hgpr") String highPrice, // 업종 지수 최고가
            @JsonProperty("bstp_nmix_lwpr") String lowPrice // 업종 지수 최저가
    ) {
    }

    // 국내 지수 기간별 상세 시세
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyIndexChartPriceItem(
            @JsonProperty("stck_bsop_date") String tradeDate, // 영업 일자
            @JsonProperty("bstp_nmix_oprc") String openPrice, // 업종 지수 시가
            @JsonProperty("bstp_nmix_hgpr") String highPrice, // 업종 지수 최고가
            @JsonProperty("bstp_nmix_lwpr") String lowPrice, // 업종 지수 최저가
            @JsonProperty("bstp_nmix_prpr") String closePrice, // 업종 지수 현재가
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 누적 거래 대금
            @JsonProperty("mod_yn") String modified // 변경 여부
    ) {
    }
}
