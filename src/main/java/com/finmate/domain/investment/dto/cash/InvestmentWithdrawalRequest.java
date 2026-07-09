package com.finmate.domain.investment.dto.cash;

import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.normal.account.BankCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 증권 계좌 -> 일반 계좌로 투자금을 출금하기 위한 dto
@Getter
@Setter
public class InvestmentWithdrawalRequest {
    @NotNull(message = "출금할 증권 계좌는 필수입니다.")
    private Long fromInvestmentId;

    @NotNull(message = "출금할 증권사는 필수입니다.")
    private SecuritiesCompanyCode fromSecuritiesCompanyCode;

    @NotNull(message = "입금 계좌는 필수입니다.")
    private Long toAccountId;

    @NotNull(message = "입금 은행은 필수입니다.")
    private BankCode toBankCode;

    @NotNull(message = "출금 금액은 필수입니다.")
    @Positive(message = "출금 금액은 0보다 커야 합니다.")
    private BigDecimal amount;
}
