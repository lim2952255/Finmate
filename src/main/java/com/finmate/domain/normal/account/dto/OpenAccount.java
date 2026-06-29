package com.finmate.domain.normal.account.dto;

import com.finmate.domain.normal.account.BankCode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAccount {
    @NotNull(message = "은행은 필수입니다.")
    private BankCode bankCode;
}
