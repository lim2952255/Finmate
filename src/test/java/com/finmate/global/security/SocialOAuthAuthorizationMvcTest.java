package com.finmate.global.security;

import com.finmate.controller.login.LoginController;
import com.finmate.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LoginController.class,
        properties = {
                "finmate.oauth.google.enabled=true",
                "finmate.oauth.google.client-id=test-client-id",
                "finmate.oauth.google.client-secret=test-client-secret",
                "finmate.oauth.kakao.enabled=true",
                "finmate.oauth.kakao.client-id=test-kakao-client-id",
                "finmate.oauth.kakao.client-secret=test-kakao-client-secret",
                "finmate.oauth.naver.enabled=true",
                "finmate.oauth.naver.client-id=test-naver-client-id",
                "finmate.oauth.naver.client-secret=test-naver-client-secret"
        }
)
@Import({
        SecurityConfig.class,
        SocialOAuthClientConfig.class
})
class SocialOAuthAuthorizationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FinMateUserDetailsService userDetailsService;

    @MockitoBean
    private FinMateOidcUserService oidcUserService;

    @MockitoBean
    private FinMateOAuth2UserService oauth2UserService;

    @Test
    @DisplayName("활성화된 소셜 로그인 공급자 링크를 모두 표시한다")
    void displaysSocialLoginLinks() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "/oauth2/authorization/google"
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "/oauth2/authorization/kakao"
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "/oauth2/authorization/naver"
                        )
                ));
    }

    @Test
    @DisplayName("Google 로그인 시작 요청은 Google 인증 서버로 이동한다")
    void redirectsToGoogleAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.startsWith(
                                "https://accounts.google.com/o/oauth2/v2/auth?"
                        )
                ));
    }

    @Test
    @DisplayName("Kakao 로그인 시작 요청은 Kakao 인증 서버로 이동한다")
    void redirectsToKakaoAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.startsWith(
                                "https://kauth.kakao.com/oauth/authorize?"
                        )
                ));
    }

    @Test
    @DisplayName("Naver 로그인 시작 요청은 Naver 인증 서버로 이동한다")
    void redirectsToNaverAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.startsWith(
                                "https://nid.naver.com/oauth2.0/authorize?"
                        )
                ));
    }
}
