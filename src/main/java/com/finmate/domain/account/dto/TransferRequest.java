package com.finmate.domain.account.dto;

import com.finmate.domain.account.BankCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TransferRequest {

    @NotBlank(message = "출금 계좌는 필수입니다.")
    @Pattern(regexp = "^\\d{6}-\\d{2}-\\d{6}$", message = "계좌번호는 000000-00-000000 형식으로 입력해주세요.")
    private String fromAccountNumber;

    @NotNull(message = "출금 은행은 필수입니다.")
    private BankCode fromBankCode;


    @NotNull(message = "입금 은행은 필수입니다.")
    private BankCode toBankCode;

    @NotBlank(message = "입금 계좌는 필수입니다.")
    @Pattern(regexp = "^\\d{6}-\\d{2}-\\d{6}$", message = "계좌번호는 000000-00-000000 형식으로 입력해주세요.")
    private String toAccountNumber;

    @NotNull(message = "이체 금액은 필수입니다.")
    @Positive(message = "이체 금액은 1원 이상이어야 합니다.")
    private BigDecimal amount;
}
