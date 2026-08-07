package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API의 종목별 투자자매매동향(일별) API 응답 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisInvestorTradeResponse(
        @JsonProperty("rt_cd") String rtCd, // API 호출 성공/실패 코드
        @JsonProperty("msg_cd") String msgCd, // API 호출 결과 메세지
        @JsonProperty("msg1") String msg1, // 사람이 읽을 수 있는 결과 메세지
        @JsonProperty("output1") InvestorTradeSummary output1, // 현재 시점의 종목 요약정보
        @JsonProperty("output2") List<DailyInvestorTrade> output2 // 날짜별 투자자 수급
) implements KisApiResponse {

    // 현재 종목 요약정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorTradeSummary(
            @JsonProperty("stck_prpr") String currentPrice, // 현재 주가
            @JsonProperty("prdy_vrss") String changeAmount, // 전일 종가 대비 가격 변화량
            @JsonProperty("prdy_vrss_sign") String changeSign, // 상승/하락/보합을 나타내는 KIS 코드
            @JsonProperty("prdy_ctrt") String changeRate, // 전일 대비 등락률
            @JsonProperty("acml_vol") String accumulatedVolume, // 현재까지의 금일 누적 거래량
            @JsonProperty("prdy_vol") String previousDayVolume, // 전일 거래량
            @JsonProperty("rprs_mrkt_kor_name") String marketName // 시장명
    ) {
    }

    // 날짜별 투자자 수급
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyInvestorTrade(
            @JsonProperty("stck_bsop_date") String tradeDate, // 기준 거래일
            @JsonProperty("stck_clpr") String closePrice, // 기준 거래일의 종가
            @JsonProperty("acml_vol") String accumulatedVolume, // 기준 거래일의 총 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 기준 거래일의 총 거래대금
            @JsonProperty("frgn_seln_vol") String foreignSellQuantity, // 외국인 매도 수량
            @JsonProperty("frgn_shnu_vol") String foreignBuyQuantity, // 외국인 매수 수량
            @JsonProperty("frgn_ntby_qty") String foreignNetBuyQuantity, // 외국인 순매수 수량
            @JsonProperty("frgn_seln_tr_pbmn") String foreignSellAmount, // 외국인 매도 금액
            @JsonProperty("frgn_shnu_tr_pbmn") String foreignBuyAmount, // 외국인 매수 금액
            @JsonProperty("frgn_ntby_tr_pbmn") String foreignNetBuyAmount, // 외국인 순매수 금액
            @JsonProperty("prsn_seln_vol") String personalSellQuantity, // 개인 매도 수량
            @JsonProperty("prsn_shnu_vol") String personalBuyQuantity, // 개인 매수 수량
            @JsonProperty("prsn_ntby_qty") String personalNetBuyQuantity, // 개인 순매수 수량
            @JsonProperty("prsn_seln_tr_pbmn") String personalSellAmount, // 개인 매도 금액
            @JsonProperty("prsn_shnu_tr_pbmn") String personalBuyAmount, // 개인 매수 금액
            @JsonProperty("prsn_ntby_tr_pbmn") String personalNetBuyAmount, // 개인 순매수 금액
            @JsonProperty("orgn_seln_vol") String institutionSellQuantity, // 기관 매도 수량
            @JsonProperty("orgn_shnu_vol") String institutionBuyQuantity, // 기관 매수 수량
            @JsonProperty("orgn_ntby_qty") String institutionNetBuyQuantity, // 기관 순매수 수량
            @JsonProperty("orgn_seln_tr_pbmn") String institutionSellAmount, // 기관 매도 금액
            @JsonProperty("orgn_shnu_tr_pbmn") String institutionBuyAmount, // 기관 매수 금액
            @JsonProperty("orgn_ntby_tr_pbmn") String institutionNetBuyAmount // 기관 순매수 금액
    ) {
    }
}
