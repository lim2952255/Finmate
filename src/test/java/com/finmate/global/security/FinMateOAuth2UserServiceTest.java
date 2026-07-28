package com.finmate.global.security;

import com.finmate.domain.user.oauth.OAuthProvider;
import com.finmate.service.user.OAuthAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinMateOAuth2UserServiceTest {

    @Test
    @DisplayName("Naver OAuth2 응답을 FinMate 로컬 Principal로 변환한다")
    void mapsNaverOAuth2UserToFinMatePrincipal() {
        // Mock 가짜 객체를 생성
        OAuthAccountService oauthAccountService =
                mock(OAuthAccountService.class);
        DefaultOAuth2UserService delegate =
                mock(DefaultOAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);
        OAuth2User oauth2User = mock(OAuth2User.class);

        // registration에 naver를 등록
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("naver");
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        // 사용자에 대한 응답정보 리턴
        when(oauth2User.getAttributes()).thenReturn(Map.of(
                "resultcode", "00",
                "message", "success",
                "response", Map.of(
                        "id", "naver-user-id",
                        "email", "naver@example.com",
                        "name", "네이버 사용자",
                        "nickname", "네이버 별명"
                )
        ));
        doReturn(java.util.List.of(
                new SimpleGrantedAuthority("OAUTH2_USER")
        )).when(oauth2User).getAuthorities();
        when(oauthAccountService.resolveOrCreate(
                OAuthProvider.NAVER,
                "naver-user-id",
                "naver@example.com",
                "네이버 사용자"
        )).thenReturn(new OAuthAccountService.ResolvedOAuthUser(
                // 새로운 네이버 사용자 생성
                3L,
                null,
                "네이버 사용자"
        ));

        FinMateOAuth2UserService service =
                new FinMateOAuth2UserService(
                        oauthAccountService,
                        delegate
                );

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(FinMateOAuth2Principal.class);
        assertThat(result.getAuthorities())
                .extracting("authority")
                .contains("OAUTH2_USER", "ROLE_USER");
        // 공급자의 사용자 응답정보를 기반으로 FinMate전용 Principal로 변환되는지를 검사한다.
        assertThat((FinMateAuthenticatedPrincipal) result)
                .extracting(
                        FinMateAuthenticatedPrincipal::getId,
                        FinMateAuthenticatedPrincipal::getDisplayName
                )
                .containsExactly(3L, "네이버 사용자");

        verify(oauthAccountService).resolveOrCreate(
                OAuthProvider.NAVER,
                "naver-user-id",
                "naver@example.com",
                "네이버 사용자"
        );
    }
}
