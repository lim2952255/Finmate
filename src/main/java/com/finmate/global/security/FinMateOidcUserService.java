package com.finmate.global.security;

import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.service.user.OAuthAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;


// Google과 같은 OIDC 로그인 처리
/*
* 외부 공급자에서 사용자 정보 조회
→ 공급자 식별
→ 외부 사용자 정보를 FinMate 공통 형식으로 변환
→ OAuthAccountService로 내부 User 조회 또는 생성
→ FinMate용 Principal 생성 후 반환
* */
@Service
public class FinMateOidcUserService
    // OAuth2UserService 서비스는 OIDC 로그인 요청 정보를 받아서 인증된 OIDC 사용자 객체를 반환하는 서비스이다.
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuthAccountService oauthAccountService; // FinMate DB에 있는 사용자와 OAuth계정을 처리한다.
    private final OidcUserService delegate; // Spring Security가 기본으로 제공하는 OIDC 사용자 조회 서비스

    @Autowired
    public FinMateOidcUserService(OAuthAccountService oauthAccountService) {
        this(oauthAccountService, new OidcUserService());
    }

    FinMateOidcUserService(OAuthAccountService oauthAccountService,
                           OidcUserService delegate) {
        this.oauthAccountService = oauthAccountService;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);// OidcUserService를 통해 OIDC 사용자를 조회한다.
        // 어떤 OAuth 제공자인지 확인한다 (Google or Kakao ..)
        OAuthProvider provider = OAuthProvider.fromRegistrationId(
                userRequest.getClientRegistration().getRegistrationId()
        );

        // Finmate 사용자 정보를 조회하거나, 없으면 새로 생성한다.
        OAuthAccountService.ResolvedOAuthUser user =
                oauthAccountService.resolveOrCreate(
                        provider,
                        oidcUser.getSubject(),
                        oidcUser.getEmail(),
                        resolveDisplayName(oidcUser)
                );

        // FinMateOidcPrincipal을 생성해서 봔환한다.
        return new FinMateOidcPrincipal(
                user.id(),
                user.userId(),
                user.displayName(),
                oidcUser
        );
    }

    // DisPlayName으로 사용할 적절할 이름을 찾는다.
    private String resolveDisplayName(OidcUser oidcUser) {
        if (oidcUser.getFullName() != null
                && !oidcUser.getFullName().isBlank()) {
            return oidcUser.getFullName();
        }

        String nickname = oidcUser.getClaimAsString("nickname");
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }

        return oidcUser.getEmail();
    }
}
