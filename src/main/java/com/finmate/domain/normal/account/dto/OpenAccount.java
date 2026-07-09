package com.finmate.domain.normal.account.dto;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.BankCode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 계좌 개설용 dto
@Getter
@Setter
public class OpenAccount {
    @NotNull(message = "은행은 필수입니다.")
    private BankCode bankCode;

    // 계좌 개설시 통화를 설정할 수 있다.
    @NotNull(message = "통화는 필수입니다.")
    private CurrencyCode currencyCode = CurrencyCode.DEFAULT;
}
