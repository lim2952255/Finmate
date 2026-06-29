package com.finmate.domain.investment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecuritiesCompanyCode {
    KIWOOM("키움증권"),
    NH_INVESTMENT("NH투자증권"),
    KB_SECURITIES("KB증권"),
    SHINHAN_SECURITIES("신한투자증권"),
    SAMSUNG_SECURITIES("삼성증권"),
    MIRAE_ASSET("미래에셋증권"),
    KOREA_INVESTMENT("한국투자증권"),
    HANA_SECURITIES("하나증권"),
    DAISHIN("대신증권"),
    MERITZ("메리츠증권"),
    TOSS_SECURITIES("토스증권"),
    KAKAO_PAY_SECURITIES("카카오페이증권");

    private final String displayName;
}
