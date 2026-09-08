package com.finmate.infra.kis.stock.disclosure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API의 시황/공시정보 응답결과를 매핑하는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisStockDisclosureResponse(
        // API 처리 성공 여부를 나타내는 코드다. KIS 공통 응답 검증에 사용한다.
        @JsonProperty("rt_cd") String rtCd,
        // 성공 또는 실패 원인을 구분하는 KIS 응답 코드다.
        @JsonProperty("msg_cd") String msgCd,
        // 처리 결과나 오류 내용을 설명하는 구체적인 KIS 응답 메시지다.
        @JsonProperty("msg1") String msg1,
        // 조회된 시황/공시 제목 목록이다.
        @JsonProperty("output") List<DisclosureTitle> output
) implements KisApiResponse {
    public KisStockDisclosureResponse {
        // KIS가 output을 생략하거나 null로 내려도 서비스는 빈 응답으로 처리한다.
        output = output == null ? List.of() : List.copyOf(output);
    }

    // KIS API로 조회한 실제 각 시황/공시 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DisclosureTitle(
            // KIS에서 제목의 상세 내용을 조회하거나 항목을 식별할 때 사용하는 일련번호다.
            @JsonProperty("cntt_usiq_srno") String contentSerialNumber,
            // 해당 제목을 제공한 뉴스·정보 제공업체의 KIS 코드다.
            @JsonProperty("news_ofer_entp_code") String providerCode,
            // 제목이 작성된 날짜다. 일반적으로 YYYYMMDD 형식으로 전달된다.
            @JsonProperty("data_dt") String dataDate,
            // 제목이 작성된 시간이다. 일반적으로 HHmmss 형식으로 전달된다.
            @JsonProperty("data_tm") String dataTime,
            // HTS에 표시되는 시황 또는 공시의 제목 내용이다.
            @JsonProperty("hts_pbnt_titl_cntt") String title,
            // KIS가 부여한 뉴스 대구분 코드다. 현재는 원본 값으로 저장만 한다.
            @JsonProperty("news_lrdv_code") String categoryCode,
            // 언론사, 거래소 등 해당 제목의 자료원 이름이다.
            @JsonProperty("dorg") String source,
            // 해당 제목과 연결된 첫 번째 관련 종목코드다.
            @JsonProperty("iscd1") String symbol1,
            // 해당 제목과 연결된 두 번째 관련 종목코드다.
            @JsonProperty("iscd2") String symbol2,
            // 해당 제목과 연결된 세 번째 관련 종목코드다.
            @JsonProperty("iscd3") String symbol3,
            // 해당 제목과 연결된 네 번째 관련 종목코드다.
            @JsonProperty("iscd4") String symbol4,
            // 해당 제목과 연결된 다섯 번째 관련 종목코드다.
            @JsonProperty("iscd5") String symbol5
    ) {
    }
}
