package com.finmate.global.security;

// 일반 사용자와 OAuth 사용자들의 Principal을 공통으로 관리하기 위해서 Interface를 구축한다.
// Principal이란 Security Context내의 Authentication 내부에 저장되는 사용자 정보를 의미한다.
public interface FinMateAuthenticatedPrincipal {

    Long getId(); // 사용자별 식별 아이디

    String getUserId(); // 사용자 아이디

    String getDisplayName(); //사용자 이름
}
