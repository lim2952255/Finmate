package com.finmate.domain.user.oauth;

// OAuthProvider의 종류, 즉 외부 인증 제공업체의 종류를 ENUM으로 관리한다.
public enum OAuthProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        return valueOf(registrationId.toUpperCase());
    }
}
