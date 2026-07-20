package com.finmate.infra.kis.exchange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KIS 해외 지수 / 환율 분봉 차트 데이터 REST API로 조회하는 클라이언트
@Component
@RequiredArgsConstructor
public class KisOverseasMarketMinuteChartPriceClient {
    private static final String MINUTE_CHART_PRICE_PATH =
            "/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice";
    private static final String MINUTE_CHART_PRICE_TR_ID = "FHKST03030200";
    private static final String REGULAR_MARKET_HOUR_CLASS_CODE = "0";
    private static final String INCLUDE_PAST_DATA = "Y";

    private final KisRestClient kisRestClient; // KIS API에 실제로 요청하여 데이터를 받는 클라이언트

    // 분봉 차트 데이터를 요청하는 메서드
    public MinuteChartPriceResponse fetchMinuteChartPrice(String marketDivisionCode,
                                                          String symbol) {
        validateRequired(marketDivisionCode, "시장 분류 코드는 필수입니다.");
        validateRequired(symbol, "지수/환율 코드는 필수입니다.");

        // KIS API 요청에 필요한 파라미터들을 설정한다.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", marketDivisionCode);
        params.put("FID_INPUT_ISCD", symbol);
        params.put("FID_HOUR_CLS_CODE", REGULAR_MARKET_HOUR_CLASS_CODE);
        params.put("FID_PW_DATA_INCU_YN", INCLUDE_PAST_DATA);

        return kisRestClient.get(
                MINUTE_CHART_PRICE_PATH,
                MINUTE_CHART_PRICE_TR_ID,
                params,
                MinuteChartPriceResponse.class);
    }

    // KIS API응답 결과를 저장하는 레코드
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MinuteChartPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output1") JsonNode output1,
            @JsonProperty("output2") JsonNode output2
    ) implements KisApiResponse {
    }
}
