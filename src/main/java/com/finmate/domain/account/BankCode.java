package com.finmate.domain.account;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankCode {
    KB_KOOKMIN("KB국민은행"),
    SHINHAN("신한은행"),
    WOORI("우리은행"),
    HANA("하나은행"),
    NH_NONGHYUP("NH농협은행"),
    IBK("IBK기업은행"),
    KDB("KDB산업은행"),
    SC_JEIL("SC제일은행"),
    CITI("한국씨티은행"),
    SH_SUHYUP("수협은행"),

    K_BANK("케이뱅크"),
    KAKAO_BANK("카카오뱅크"),
    TOSS_BANK("토스뱅크"),

    IM_BANK("iM뱅크"),
    BNK_BUSAN("BNK부산은행"),
    BNK_KYONGNAM("BNK경남은행"),
    GWANGJU("광주은행"),
    JEONBUK("전북은행"),
    JEJU("제주은행"),

    KOREA_POST("우체국"),
    SAEMAUL("새마을금고"),
    SHINHYUP("신협"),
    SBI_SAVINGS("SBI저축은행");

    private final String displayName;
}
