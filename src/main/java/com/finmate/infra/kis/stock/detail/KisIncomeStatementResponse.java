package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;

// KIS API의 국내주식 손익계산서 API 응답 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisIncomeStatementResponse(
        @JsonProperty("rt_cd") String rtCd, // API 호출 성공/실패 코드
        @JsonProperty("msg_cd") String msgCd, // API 호출 결과 메세지
        @JsonProperty("msg1") String msg1, // 사람이 읽을 수 있는 결과 메세지
        @JsonProperty("output") List<IncomeStatement> output // 실제 손익계산서 데이터
) implements KisApiResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomeStatement(
            @JsonProperty("stac_yymm") String settlementYearMonth, // 결산년월
            @JsonProperty("sale_account") String revenue, // 매출액
            @JsonProperty("sale_cost") String costOfSales, // 매출 원가
            @JsonProperty("sale_totl_prfi") String grossProfit, // 매출 총이익: 매출액 - 매출 원가
            @JsonProperty("depr_cost") String depreciationExpense, // 감가상각비: 건물, 기계, 설비 같은 장기자산의 가치 감소를 회계상 비용으로 나누어 반영한 금액
            @JsonProperty("sell_mang") String sellingAndAdministrativeExpense, // 판매비와 관리비 (판관비)
            @JsonProperty("bsop_prti") String operatingProfit, // 영업이익: 매출 총이익 - 판관비
            @JsonProperty("bsop_non_ernn") String nonOperatingIncome, // 영업외수익
            @JsonProperty("bsop_non_expn") String nonOperatingExpense, // 영업외비용
            @JsonProperty("op_prfi") String ordinaryProfit, // 경상이익
            @JsonProperty("spec_prfi") String extraordinaryProfit, // 특별이익
            @JsonProperty("spec_loss") String extraordinaryLoss, // 특별비용
            @JsonProperty("thtr_ntin") String netIncome // 당기순이 : 모든 수익과 비용을 반영한 뒤, 최종적으로 남은 이익
    ) {
    }
}
