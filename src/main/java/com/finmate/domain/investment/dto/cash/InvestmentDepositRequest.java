package com.finmate.domain.investment.dto.cash;

import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.normal.account.BankCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 일반 계좌 -> 증권 계좌로 투자금을 입금하기 위한 dto
@Getter
@Setter
public class InvestmentDepositRequest {
    @NotNull(message = "출금 계좌는 필수입니다.")
    private Long fromAccountId;

    @NotNull(message = "출금 은행은 필수입니다.")
    private BankCode fromBankCode;

    @NotNull(message = "입금할 증권 계좌는 필수입니다.")
    private Long toInvestmentId;

    @NotNull(message = "입금할 증권사는 필수입니다.")
    private SecuritiesCompanyCode toSecuritiesCompanyCode;

    @NotNull(message = "입금 금액은 필수입니다.")
    @Positive(message = "입금 금액은 1원 이상이어야 합니다.")
    private BigDecimal amount;
}
