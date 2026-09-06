package com.finmate.global.security;

import com.finmate.controller.login.AuthApiController;
import com.finmate.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                AuthApiController.class
        },
        properties = {
                "finmate.oauth.google.enabled=true",
                "finmate.oauth.google.client-id=test-client-id",
                "finmate.oauth.google.client-secret=test-client-secret",
                "finmate.oauth.kakao.enabled=true",
                "finmate.oauth.kakao.client-id=test-kakao-client-id",
                "finmate.oauth.kakao.client-secret=test-kakao-client-secret",
                "finmate.oauth.naver.enabled=true",
                "finmate.oauth.naver.client-id=test-naver-client-id",
                "finmate.oauth.naver.client-secret=test-naver-client-secret",
                "finmate.frontend-base-url=http://localhost:5173"
        }
)
@Import({
        SecurityConfig.class,
        SocialOAuthClientConfig.class
})
class SocialOAuthAuthorizationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FinMateUserDetailsService userDetailsService;

    @MockitoBean
    private FinMateOidcUserService oidcUserService;

    @MockitoBean
    private FinMateOAuth2UserService oauth2UserService;

    @Test
    @DisplayName("활성화된 소셜 로그인 공급자 정보를 JSON으로 제공한다")
    void providesSocialLoginOptions() throws Exception {
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true))
                .andExpect(jsonPath("$.kakao").value(true))
                .andExpect(jsonPath("$.naver").value(true));
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

    @Test
    @DisplayName("OAuth 로그인 시작 전에 React 복귀 경로를 세션에 저장한다")
    void storesFrontendRedirectBeforeStartingOAuthLogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/google")
                        .param("redirect", "/investments/portfolio?tab=holdings"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/oauth2/authorization/google"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getAttribute(
                OAuth2RedirectSessionStore.SESSION_ATTRIBUTE
        )).isEqualTo("/investments/portfolio?tab=holdings");
    }

    @Test
    @DisplayName("OAuth 로그인은 외부 주소를 복귀 경로로 저장하지 않는다")
    void replacesExternalOAuthRedirectWithHome() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/google")
                        .param("redirect", "//evil.example/path"))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute(
                OAuth2RedirectSessionStore.SESSION_ATTRIBUTE
        )).isEqualTo("/home");
    }

    @Test
    @DisplayName("OAuth 로그인 성공은 저장된 backend 요청 대신 원래 React 경로로 이동한다")
    void redirectsOAuthSuccessToOriginalFrontendPathInsteadOfSavedBackendRequest() throws Exception {
        OAuth2LoginAuthenticationFilter filter = oauth2LoginAuthenticationFilter();
        AuthenticationSuccessHandler successHandler = (AuthenticationSuccessHandler)
                ReflectionTestUtils.getField(filter, "successHandler");
        MockHttpSession session = new MockHttpSession();

        MockHttpServletRequest authorizationRequest = new MockHttpServletRequest();
        authorizationRequest.setSession(session);
        new OAuth2RedirectSessionStore().save(
                authorizationRequest,
                "/investments/portfolio?tab=holdings"
        );

        MockHttpServletRequest backendRequest = new MockHttpServletRequest("GET", "/");
        backendRequest.setScheme("http");
        backendRequest.setServerName("localhost");
        backendRequest.setServerPort(8080);
        backendRequest.setSession(session);
        new HttpSessionRequestCache().saveRequest(backendRequest, new MockHttpServletResponse());

        MockHttpServletRequest callbackRequest = new MockHttpServletRequest(
                "GET",
                "/login/oauth2/code/google"
        );
        callbackRequest.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(successHandler).isNotNull();
        successHandler.onAuthenticationSuccess(
                callbackRequest,
                response,
                mock(Authentication.class)
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/investments/portfolio?tab=holdings");
        assertThat(new HttpSessionRequestCache().getRequest(callbackRequest, response)).isNull();
        assertThat(session.getAttribute(OAuth2RedirectSessionStore.SESSION_ATTRIBUTE)).isNull();
    }

    private OAuth2LoginAuthenticationFilter oauth2LoginAuthenticationFilter() {
        return securityFilterChain.getFilters().stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
