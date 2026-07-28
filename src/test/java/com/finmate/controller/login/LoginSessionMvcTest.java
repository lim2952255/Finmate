package com.finmate.controller.login;

import com.finmate.global.security.FinMatePrincipal;
import com.finmate.global.security.FinMateOAuth2UserService;
import com.finmate.global.security.FinMateOidcUserService;
import com.finmate.global.security.FinMateUserDetailsService;
import com.finmate.global.security.SecurityConfig;
import com.finmate.service.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인, 세션 저장, 세션 재사용, 로그아웃을 검증한다.
// 이때 @WebMvcTest는 전체 애플리케이션을 띄우는 것이 아니라, MVC 관련 구성만 load한다.
@WebMvcTest(controllers = {
        LoginController.class,
        LoginSessionMvcTest.ProtectedRouteController.class
}, properties = {
        "finmate.oauth.google.enabled=false",
        "finmate.oauth.kakao.enabled=false",
        "finmate.oauth.naver.enabled=false"
})
@Import({ // 관련 설정을 import한다.
        SecurityConfig.class,
        LoginSessionMvcTest.ProtectedRouteController.class
})
class LoginSessionMvcTest {

    // 테스트용 사용자 계정 생성
    private static final String USER_ID = "finmate01";
    private static final String PASSWORD = "password1!";

    // Http 요청을 대신 전달하고 응답을 받는 MockMvc
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 실제 Bean의 기능은 필요없지만, 객체를 연관관계 주입하기 위해서 가짜 객체를 주입한다.
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FinMateUserDetailsService userDetailsService;

    @MockitoBean
    private FinMateOidcUserService oidcUserService;

    @MockitoBean
    private FinMateOAuth2UserService oauth2UserService;

    // 매 테스트를 호출하기 전에 실행된다.
    @BeforeEach
    void setUpAuthenticatedUser() {
        when(userDetailsService.loadUserByUsername(USER_ID)).thenReturn(principal());
    }

    @Test
    @DisplayName("AUTH-002: 로그인 성공 시 SecurityContext를 서버 세션에 저장한다")
    void storesSecurityContextInServerSessionAfterSuccessfulLogin() throws Exception {
        MockHttpSession session = login(); // 로그인 성공 후 세션을 리턴받는다.

        assertThat(session).isNotNull();

        // 서버세션에 Princical이 정상적으로 저장되는지를 검사한다.
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isInstanceOfSatisfying(SecurityContext.class, securityContext ->
                        assertThat(securityContext.getAuthentication().getPrincipal())
                                .isInstanceOfSatisfying(FinMatePrincipal.class, principal -> {
                                    assertThat(principal.getId()).isEqualTo(1L);
                                    assertThat(principal.getPassword()).isNull(); // 로그인 성공 후에는 패스워드 정보는 지워져야 한다.
                                }));

        // 로그인 성공 후 /protected 페이지에 접속하면 접속이 되어야 한다.
        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(USER_ID))
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("AUTH-002: 로그인 세션은 연속된 보호 경로 요청에서 재사용된다")
    void reusesSuccessfulLoginSessionForConsecutiveProtectedRequests() throws Exception {
        MockHttpSession session = login(); // 로그인 성공

        // 로그인 성공후에는 보호 경로에 여러번 반복해서 접근이 가능해야 한다.
        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(userDetailsService).loadUserByUsername(USER_ID);
    }

    @Test
    @DisplayName("AUTH-002: 로그인 성공 후 저장된 원래 요청으로 이동한다")
    void redirectsToSavedRequestAfterLogin() throws Exception {
        // 로그인 하기전에 보호 경로에 요청을 보내면, 원래 요청을 저장하고 /loin 경로로 이동한다.
        MockHttpSession requestCacheSession = (MockHttpSession) mockMvc.perform(get("/protected"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(requestCacheSession).isNotNull();

        // 로그인 성공 후에는 기존에 이동하려고 했던 /protected 경로로 다시 리다이렉트된다.
        mockMvc.perform(post("/login")
                        .session(requestCacheSession)
                        .with(csrf())
                        .param("userId", USER_ID)
                        .param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/protected?continue"))
                .andExpect(authenticated().withUsername(USER_ID));
    }

    @Test
    @DisplayName("AUTH-002: 알 수 없는 세션 식별자의 보호 경로 요청은 로그인 페이지로 이동한다")
    void redirectsProtectedRequestWhenSessionIdentifierIsUnknown() throws Exception {
        // 알수없는 세션 식별자로 요청이 들어온 경우에는 이를 거부하고 /login 페이지로 이동한다.
        mockMvc.perform(get("/protected")
                        .cookie(new Cookie("JSESSIONID", "unknown-session")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("AUTH-002: 잘못된 비밀번호는 인증하지 않는다")
    void rejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("userId", USER_ID)
                        // 잘못된 패스워드를 입력하면 로그인에 실패해야 한다.
                        .param("password", "wrong-password"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("AUTH-002: CSRF 토큰이 없는 로그인 요청은 거부한다")
    void rejectsLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                // CSRF 토큰이 없는 로그인 요청은 거부한다.
                        .param("userId", USER_ID)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());

        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("AUTH-003: 로그아웃은 기존 로그인 세션을 무효화한다")
    void invalidatesSessionOnLogout() throws Exception {
        MockHttpSession session = login();

        // 로그아웃시에 기존 로그인 세션을 제거한다.
        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("AUTH-003: 로그아웃 후 기존 세션 식별자로 보호 경로에 접근할 수 없다")
    void redirectsProtectedRequestAfterLogoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();
        String invalidatedSessionId = session.getId();

        // 로그아웃시에는 기존 로그인세션으로 다시 로그인할 수 없다.
        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isFound());

        mockMvc.perform(get("/protected")
                        .cookie(new Cookie("JSESSIONID", invalidatedSessionId)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("AUTH-003: CSRF 토큰이 없는 로그아웃 요청은 거부한다")
    void rejectsLogoutWithoutCsrfToken() throws Exception {
        MockHttpSession session = login();

        // CSRF 토큰이 없는 로그아웃 요청도 거부한다.
        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(USER_ID));
    }

    // 로그인을 요청하고, 테스트용 파라미터를 입력하고 결과 세션을 리턴한다.
    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("userId", USER_ID)
                        .param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(USER_ID))
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    // 테스트용 FinMatePrincipal을 리턴한다.
    private FinMatePrincipal principal() {
        return new FinMatePrincipal(
                1L,
                USER_ID,
                "Fin Mate",
                passwordEncoder.encode(PASSWORD),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // 권한이 있는 사용자만 접근할 수 있는 /protected 경로 설정
    @Controller
    static class ProtectedRouteController {

        @GetMapping("/protected")
        @ResponseBody
        String protectedPage(
                @org.springframework.security.core.annotation.AuthenticationPrincipal
                FinMatePrincipal principal
        ) {
            return principal.getId().toString();
        }
    }
}
