package com.finmate.domain.investment.dto.exchange;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 사용자가 환전을 요청할때, 사용자가 입력한 환전 정보를 담는 DTO
@Getter
@Setter
public class InvestmentCurrencyExchangeRequest {
    @NotNull(message = "환전할 증권 계좌는 필수입니다.")
    private Long investmentId;

    @NotNull(message = "증권사는 필수입니다.")
    private SecuritiesCompanyCode securitiesCompanyCode;

    // A 통화 -> B 통화 환전

    @NotNull(message = "환전 전 통화는 필수입니다.")
    private CurrencyCode fromCurrencyCode; // 환전 전 통화 (A 통화)

    @NotNull(message = "환전 후 통화는 필수입니다.")
    private CurrencyCode toCurrencyCode; // 환전 후 통화 (B 통화)

    @NotNull(message = "환전 금액은 필수입니다.")
    @Positive(message = "환전 금액은 0보다 커야 합니다.")
    private BigDecimal fromAmount;
}
