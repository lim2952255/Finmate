package com.finmate.global.security;

import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.service.user.OAuthAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinMateOidcUserServiceTest {

    @Test
    @DisplayName("Google OIDC 사용자를 FinMate 로컬 Principal로 변환한다")
    void mapsGoogleOidcUserToFinMatePrincipal() {
        OAuthAccountService oauthAccountService =
                mock(OAuthAccountService.class);
        OidcUserService delegate = mock(OidcUserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getFullName()).thenReturn("Google User");
        doReturn(
                java.util.List.of(
                        new SimpleGrantedAuthority("OIDC_USER")
                )
        ).when(oidcUser).getAuthorities();
        when(oauthAccountService.resolveOrCreate(
                OAuthProvider.GOOGLE,
                "google-subject",
                "user@example.com",
                "Google User"
        )).thenReturn(new OAuthAccountService.ResolvedOAuthUser(
                1L,
                null,
                "Google User"
        ));

        FinMateOidcUserService service =
                new FinMateOidcUserService(oauthAccountService, delegate);

        OidcUser result = service.loadUser(request);

        assertThat(result).isInstanceOf(FinMateOidcPrincipal.class);
        assertThat(result.getAuthorities())
                .extracting("authority")
                .contains("OIDC_USER", "ROLE_USER");
        assertThat((FinMateAuthenticatedPrincipal) result)
                .extracting(
                        FinMateAuthenticatedPrincipal::getId,
                        FinMateAuthenticatedPrincipal::getDisplayName
                )
                .containsExactly(1L, "Google User");

        verify(oauthAccountService).resolveOrCreate(
                OAuthProvider.GOOGLE,
                "google-subject",
                "user@example.com",
                "Google User"
        );
    }

    @Test
    @DisplayName("Kakao OIDC nickname을 FinMate 표시 이름으로 사용한다")
    void mapsKakaoNicknameToFinMatePrincipal() {
        OAuthAccountService oauthAccountService =
                mock(OAuthAccountService.class);
        OidcUserService delegate = mock(OidcUserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("kakao");
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("kakao-subject");
        when(oidcUser.getEmail()).thenReturn(null);
        when(oidcUser.getFullName()).thenReturn(null);
        when(oidcUser.getClaimAsString("nickname")).thenReturn("카카오 사용자");
        doReturn(java.util.List.of()).when(oidcUser).getAuthorities();
        when(oauthAccountService.resolveOrCreate(
                OAuthProvider.KAKAO,
                "kakao-subject",
                null,
                "카카오 사용자"
        )).thenReturn(new OAuthAccountService.ResolvedOAuthUser(
                2L,
                null,
                "카카오 사용자"
        ));

        FinMateOidcUserService service =
                new FinMateOidcUserService(oauthAccountService, delegate);

        OidcUser result = service.loadUser(request);

        assertThat((FinMateAuthenticatedPrincipal) result)
                .extracting(
                        FinMateAuthenticatedPrincipal::getId,
                        FinMateAuthenticatedPrincipal::getDisplayName
                )
                .containsExactly(2L, "카카오 사용자");

        verify(oauthAccountService).resolveOrCreate(
                OAuthProvider.KAKAO,
                "kakao-subject",
                null,
                "카카오 사용자"
        );
    }
}
