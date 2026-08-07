package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API의 국내주식 재무비율 API의 응답을 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisFinancialRatioResponse(
        @JsonProperty("rt_cd") String rtCd, // API 호출 성공/실패 코드
        @JsonProperty("msg_cd") String msgCd, // API 호출 결과 메세지
        @JsonProperty("msg1") String msg1, // 사람이 읽을 수 있는 결과 메세지
        @JsonProperty("output") List<FinancialRatio> output // 실제 재무비율 응답데이터
) implements KisApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialRatio(
            @JsonProperty("stac_yymm") String settlementYearMonth, // 결산년월
            @JsonProperty("grs") String salesGrowthRate, // 매출액 증가율
            @JsonProperty("bsop_prfi_inrt") String operatingProfitGrowthRate, // 영업이익 증가율
            @JsonProperty("ntin_inrt") String netIncomeGrowthRate, // 당기순이익 증가율
            @JsonProperty("roe_val") String roe, // ROE : 자기자본 이익률 = (당기순이익 / 자기자본) * 100
            @JsonProperty("eps") String eps, // EPS : 주당 순이익 = 당기순이익 / 주식 수
            @JsonProperty("sps") String salesPerShare, // SPS: 주당 매출액 = 매출액 / 주식 수
            @JsonProperty("bps") String bps, // BPS : 주당 순자산 = 자기자본 / 주식 수
            @JsonProperty("rsrv_rate") String reserveRatio, // 유보율 (회사가 자본금 대비 이익잉여금·자본잉여금 등을 얼마나 많이 쌓아두고 있는지를 나타내는 재무지표)
            @JsonProperty("lblt_rate") String debtRatio // 부채비율 (기업 자기자본에 비해 부채가 얼마나 많은지 보는 지표)
    ) {
    }
}
