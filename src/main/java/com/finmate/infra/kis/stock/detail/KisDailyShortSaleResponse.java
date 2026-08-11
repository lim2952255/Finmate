package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API로부터 받은 일별 공매 조회 데이터를 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDailyShortSaleResponse(
        @JsonProperty("rt_cd") String rtCd, // KIS API 응답 결과 코드
        @JsonProperty("msg_cd") String msgCd, // KIS API 응답 메세지 코드
        @JsonProperty("msg1") String msg1, // KIS API 응답 결과 메세지
        @JsonProperty("output2") List<DailyShortSale> output2 // 실제 일별 공매도 데이터 목록
) implements KisApiResponse {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyShortSale(
            @JsonProperty("stck_bsop_date") String tradeDate, // 거래일자
            @JsonProperty("stck_clpr") String closePrice, // 종가
            @JsonProperty("acml_vol") String accumulatedVolume, // 전체 누적 거래량
            @JsonProperty("ssts_cntg_qty") String shortSaleQuantity, // 공매도 거래량
            @JsonProperty("ssts_vol_rlim") String shortSaleVolumeRatio, // 전체 거래량 중 공매도 비율
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 전체 거래대금
            @JsonProperty("ssts_tr_pbmn") String shortSaleTradeAmount, // 공매도 거래대금
            @JsonProperty("ssts_tr_pbmn_rlim") String shortSaleTradeAmountRatio, // 전체 거래대금 중 공매도 거래대금 비율
            @JsonProperty("avrg_prc") String averagePrice // 평균가격
    ) {
    }
}
