package com.finmate.global.security;

import com.finmate.controller.home.HomeController;
import com.finmate.controller.login.LoginController;
import com.finmate.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
// 로그인, 세션 저장, 세션 재사용, 로그아웃을 검증한다.
// 이때 @WebMvcTest는 전체 애플리케이션을 띄우는 것이 아니라, MVC 관련 구성만 load한다.
@WebMvcTest(controllers = {
        HomeController.class,
        LoginController.class,
        SecurityAuthorizationMvcTest.ProtectedRouteController.class
}, properties = {
        "finmate.oauth.google.enabled=false",
        "finmate.oauth.kakao.enabled=false",
        "finmate.oauth.naver.enabled=false"
})
@Import({
        SecurityConfig.class,
        SecurityAuthorizationMvcTest.ProtectedRouteController.class
})
class SecurityAuthorizationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    // 실제 Bean의 기능은 필요없지만, 객체를 연관관계 주입하기 위해서 가짜 객체를 주입한다.
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FinMateUserDetailsService userDetailsService;

    @MockitoBean
    private FinMateOidcUserService oidcUserService;

    @MockitoBean
    private FinMateOAuth2UserService oauth2UserService;

    @Test
    @DisplayName("AUTH-001: 비로그인 사용자는 루트 경로에서 세션 생성 없이 홈으로 이동한다")
    void allowsUnauthenticatedAccessToRootPath() throws Exception {
        // 비로그인 사용자도 홈페이지 경로로는 이동할 수 있ㄷ.
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/home"))
                .andExpect(unauthenticated())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @ParameterizedTest(name = "allows unauthenticated access to public path {0}")
    @DisplayName("AUTH-001: 비로그인 사용자는 공개 경로에 접근할 수 있다")
    @ValueSource(strings = {"/home", "/login", "/signup"})
    void allowsUnauthenticatedAccessToPublicPath(String publicPath) throws Exception {
        // 비로그인 사용자는 공개 경로에 접근할 수 있다.
        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk())
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("AUTH-001: 비로그인 사용자는 정적 리소스에 세션 생성 없이 접근할 수 있다")
    void allowsUnauthenticatedAccessToStaticResource() throws Exception {
        // 비로그인 사용자도 정적 리소스에는 접근할 수 있다.
        mockMvc.perform(get("/css/common.css"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @ParameterizedTest(name = "redirects unauthenticated access to protected path {0}")
    @DisplayName("AUTH-001: 비로그인 사용자의 보호 경로 요청은 로그인 페이지로 이동한다")
    @ValueSource(strings = {"/accounts", "/investments", "/orders"})
    void redirectsUnauthenticatedAccessToProtectedPath(String protectedPath) throws Exception {
        // 비로그인 사용자는 보호 경로에 접근할 수 없다.
        mockMvc.perform(get(protectedPath))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(unauthenticated());
    }

    // 권한이 있는 사용자만 접근할 수 있는 경로 설정
    @Controller
    static class ProtectedRouteController {

        @GetMapping({"/accounts", "/investments", "/orders"})
        String protectedPage() {
            return "protected";
        }
    }
}
