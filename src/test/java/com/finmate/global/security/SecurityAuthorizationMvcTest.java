package com.finmate.global.security;

import com.finmate.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 화면이 아닌 보호 API 요청에 Spring Security가 적용되는지 검증한다.
@WebMvcTest(controllers = {
        SecurityAuthorizationMvcTest.ProtectedApiController.class
}, properties = {
        "finmate.oauth.google.enabled=false",
        "finmate.oauth.kakao.enabled=false",
        "finmate.oauth.naver.enabled=false"
})
@Import({
        SecurityConfig.class,
        SecurityAuthorizationMvcTest.ProtectedApiController.class
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
    @DisplayName("AUTH-001: 비로그인 사용자의 보호 API 요청은 401을 반환한다")
    void rejectsUnauthenticatedAccessToProtectedApi() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Controller
    static class ProtectedApiController {

        @GetMapping("/api/protected")
        String protectedApi() {
            return "protected";
        }
    }
}
