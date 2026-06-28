package com.finmate.domain.account.dto;

import com.finmate.domain.account.BankCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAccount {
    @NotBlank(message = "계좌번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}-\\d{2}-\\d{6}$", message = "계좌번호는 000000-00-000000 형식으로 입력해주세요.")
    private String accountNumber;

    @NotNull(message = "은행은 필수입니다.")
    private BankCode bankCode;
}
