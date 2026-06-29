package com.finmate.domain.investment.dto;

import com.finmate.domain.investment.SecuritiesCompanyCode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenInvestment {
    @NotNull(message = "증권사는 필수입니다.")
    private SecuritiesCompanyCode securitiesCompanyCode;
}
