package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

// KIS API의 재무 현재가 시세 API의 응답을 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisCurrentPriceResponse(
        @JsonProperty("rt_cd") String rtCd, // API 호출 성공/실패 코드
        @JsonProperty("msg_cd") String msgCd, // API 호출 결과 메세지
        @JsonProperty("msg1") String msg1, // 사람이 읽을 수 있는 결과 메세지
        @JsonProperty("output") CurrentPrice output // 실제 재무 현재가 시세 응답데이터
) implements KisApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentPrice(
            @JsonProperty("stck_shrn_iscd") String symbol, // 종목 코드
            @JsonProperty("rprs_mrkt_kor_name") String marketName, // 대표 시장이름(KOSPI / KOSDAQ)
            @JsonProperty("stck_prpr") String currentPrice, // 종목 현재가
            @JsonProperty("prdy_vrss") String changeAmount, // 전일 종가 대비 가격 변화량
            @JsonProperty("prdy_vrss_sign") String changeSign, // 상승/하락 여부를 나타내는 KIS 코드
            @JsonProperty("prdy_ctrt") String changeRate, // 전일대비 등락률
            @JsonProperty("acml_vol") String accumulatedVolume, // 금일 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount, // 금일 누적 거래대금
            @JsonProperty("stck_oprc") String openPrice, // 금일 시가
            @JsonProperty("stck_hgpr") String highPrice, // 금일 고가
            @JsonProperty("stck_lwpr") String lowPrice, // 금일 저가
            @JsonProperty("lstn_stcn") String listedShareCount, // 상장 주식수
            @JsonProperty("hts_avls") String marketCapitalization, // 시가총액
            @JsonProperty("per") String per, // 현재 주가 / EPS: 주식이 이익의 몇배에 거래되고 있는지
            @JsonProperty("pbr") String pbr, // 현재 주가 / BPS: 주식이 순자산의 몇배의 거래되고 있는지
            @JsonProperty("eps") String eps, // 주당 순이익
            @JsonProperty("bps") String bps, // 주당 순자산
            @JsonProperty("w52_hgpr") String fiftyTwoWeekHighPrice, // 최근 52주 최고가
            @JsonProperty("w52_hgpr_date") String fiftyTwoWeekHighDate, // 52주 최고가를 기록한 날짜
            @JsonProperty("w52_hgpr_vrss_prpr_ctrt") String fiftyTwoWeekHighToCurrentRate, // 52주 최고가와 현재가의 차이 비율
            @JsonProperty("w52_lwpr") String fiftyTwoWeekLowPrice, // 최근 52주 최저가
            @JsonProperty("w52_lwpr_date") String fiftyTwoWeekLowDate, // 52주 최저가를 기록한 날짜
            @JsonProperty("w52_lwpr_vrss_prpr_ctrt") String fiftyTwoWeekLowToCurrentRate, // 52주 최저가와 현재가의 차이 비율
            @JsonProperty("frgn_hldn_qty") String foreignHoldingQuantity, // 외국인이 현재 보유하고 있는 주식 수
            @JsonProperty("hts_frgn_ehrt") String foreignExhaustionRate, // 외국인 한도 소진율
            @JsonProperty("frgn_ntby_qty") String foreignNetBuyQuantity, // 외국인 순매수 수량
            @JsonProperty("whol_loan_rmnd_rate") String totalLoanBalanceRate // 전체 융자 잔고 비율
            // 신용융자: 증권사에서 돈을 빌려 주식을 사는것 (미수거래)
            // 융자 잔고: 신용융자 매수 물량이 시장에 얼마나 남아있는지를 나타낸 값
    ) {
    }
}
