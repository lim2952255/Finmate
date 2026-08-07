package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API의 국내주식 대차대조표 API 응답 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisBalanceSheetResponse(
        @JsonProperty("rt_cd") String rtCd, // API 호출 성공/실패 코드
        @JsonProperty("msg_cd") String msgCd, // API 호출 결과 메세지
        @JsonProperty("msg1") String msg1, // 사람이 읽을 수 있는 결과 메세지
        @JsonProperty("output") List<BalanceSheet> output // 실제 대차대조표 데이터
) implements KisApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BalanceSheet(
            @JsonProperty("stac_yymm") String settlementYearMonth, // 결산년월
            @JsonProperty("cras") String currentAssets, // 유동자산
            @JsonProperty("fxas") String nonCurrentAssets, // 고정자산
            @JsonProperty("total_aset") String totalAssets, // 총자산
            @JsonProperty("flow_lblt") String currentLiabilities, // 유동부채
            @JsonProperty("fix_lblt") String nonCurrentLiabilities, // 고정부채
            @JsonProperty("total_lblt") String totalLiabilities, // 총부채
            @JsonProperty("cpfn") String capitalStock, // 자본금
            @JsonProperty("cfp_surp") String capitalSurplus, // 자본잉여금
            @JsonProperty("prfi_surp") String retainedEarnings, // 이익잉여금
            @JsonProperty("total_cptl") String totalEquity // 총자본 / 자기자본
    ) {
    }
}
