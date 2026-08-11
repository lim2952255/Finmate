package com.finmate.infra.kis.stock.detail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;

import java.util.List;
import java.util.Map;

// KIS API로부터 받은 일별 대차거래 데이터를 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDailyLoanTransactionResponse(
        @JsonProperty("rt_cd") String rtCd, // KIS API 응답 결과 코드
        @JsonProperty("msg_cd") String msgCd, // KIS API 응답 메세지 코드
        @JsonProperty("msg1") String msg1, // KIS API 응답 결과 메세지
        @JsonProperty("output1") List<Map<String, String>> output1 // 실제 일별 대차거래 데이터 목록
) implements KisApiResponse {
    public List<Map<String, String>> rows() {
        return output1 == null ? List.of() : List.copyOf(output1);
    }
}
