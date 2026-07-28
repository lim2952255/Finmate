package com.finmate.global.security;

import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.service.user.OAuthAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

// Naver or 카카오 같은 일반 OAuth2 로그인 처리
/*
* 외부 공급자에서 사용자 정보 조회
→ 공급자 식별
→ 외부 사용자 정보를 FinMate 공통 형식으로 변환
→ OAuthAccountService로 내부 User 조회 또는 생성
→ FinMate용 Principal 생성 후 반환
* */
@Service
public class FinMateOAuth2UserService
        // OAuth2UserService는 OAuth2UserRequest를 받아서 OAuth2User를 반환하는 서비스이다.
        // 스프링 시큐리티 OAuth2 사용자 정보를 조회할때 해당 인터페이스의 loadUser 메서드를 호출한다.
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuthAccountService oauthAccountService; // FinMate DB에 있는 사용자와 OAuth계정을 처리한다.
    private final DefaultOAuth2UserService delegate; // spring Security가 기본으로 제공하는 일반 OAuth2 사용자 조회 서비스다.

    @Autowired
    public FinMateOAuth2UserService(
            OAuthAccountService oauthAccountService
    ) {
        this(oauthAccountService, new DefaultOAuth2UserService());
    }

    FinMateOAuth2UserService(
            OAuthAccountService oauthAccountService,
            DefaultOAuth2UserService delegate
    ) {
        this.oauthAccountService = oauthAccountService;
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        // OAuth2UserRequest에는 ClientRegistration, Access Token, 추가 파라미터와 같은 정보들이 담겨있다.
        // delegate를 통해서 외부 공급자의 API를 호출해서 외부 사용자 정보가 담긴 OAuth2User를 리턴한다.
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        // 공급자(Provider)정보를 반환받는다.
        OAuthProvider provider = OAuthProvider.fromRegistrationId(
                userRequest.getClientRegistration().getRegistrationId()
        );
        // 공급자별로 공급자 응답을 파싱한다.
        ProviderUserInfo providerUserInfo =
                extractProviderUserInfo(provider, oauth2User);

        // 파싱한 공급자 응답을 기반으로 사용자 정보를 DTO에 담고, 이를 기반으로 FinMateOAuth2Principal 객체를 생성해서 리턴한다.
        OAuthAccountService.ResolvedOAuthUser user =
                oauthAccountService.resolveOrCreate(
                        provider,
                        providerUserInfo.subject(),
                        providerUserInfo.email(),
                        providerUserInfo.displayName()
                );

        return new FinMateOAuth2Principal(
                user.id(),
                user.userId(),
                user.displayName(),
                oauth2User
        );
    }

    // 공급자별로 공급자 응답을 파싱한다.
    // Provider가 제공한 응답 정보를 FinMate내부에서 사용하는 공통 정보로 변환한다.
    private ProviderUserInfo extractProviderUserInfo(
            OAuthProvider provider,
            OAuth2User oauth2User
    ) {
        // 네이버가 아닌 공급자는 처리하지 않는다.
        if (provider != OAuthProvider.NAVER) {
            throw invalidUserInfo(
                    "일반 OAuth2 사용자 응답을 지원하지 않는 공급자입니다: "
                            + provider
            );
        }

        // 공급자의 응답 정보 조회
        Object responseValue = oauth2User.getAttributes().get("response");
        if (!(responseValue instanceof Map<?, ?> response)) {
            throw invalidUserInfo(
                    "Naver 사용자 정보에 response 객체가 없습니다."
            );
        }

        String subject = stringValue(response.get("id"));
        if (subject == null) {
            throw invalidUserInfo(
                    "Naver 사용자 정보에 고유 식별자 id가 없습니다."
            );
        }

        // 공급자 응답에서 id, email, displayName을 받아서 ProviderUserInfo 객체에 담는다.
        String email = stringValue(response.get("email"));
        String displayName = firstNonBlank(
                stringValue(response.get("name")),
                stringValue(response.get("nickname")),
                email
        );
        return new ProviderUserInfo(subject, email, displayName);
    }

    private String stringValue(Object value) {
        if (!(value instanceof String stringValue)
                || stringValue.isBlank()) {
            return null;
        }
        return stringValue;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private OAuth2AuthenticationException invalidUserInfo(String message) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info"),
                message
        );
    }

    // Provider가 제공한 응답 정보를 FinMate내부에서 사용하는 공통 정보로 변환한다.
    // 즉 외부 공급자가 제공한 공통화된 객체정보
    private record ProviderUserInfo(
            String subject,
            String email,
            String displayName
    ) {
    }
}
