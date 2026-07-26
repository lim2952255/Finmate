package com.finmate.controller.login;

import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.global.interceptor.InterceptorConfig;
import com.finmate.service.user.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 성공 후 세션이 생성·유지되고, 로그아웃 시 세션이 무효화되는 전체 MVC 세션 흐름을 검증한다.
// 실제 톰캣 서버와 브라우저를 띄우지 않고, MockMvc가 Spring MVC 요청 처리 과정을 내부에서 흉내낸다.

/*
* 실제 애플리케이션 흐름
* 브라우저
  ↓ 실제 TCP/HTTP 요청
톰캣
  ↓ HttpServletRequest 생성
Filter
  ↓
DispatcherServlet
  ↓
HandlerMapping
  ↓
HandlerInterceptor
  ↓
Controller
  ↓
ViewResolver 또는 ResponseBody 처리
  ↓
HttpServletResponse
  ↓ 실제 HTTP 응답
브라우저
* */

/* MockMvc 흐름
* 테스트 코드
  ↓ mockMvc.perform(...)
MockHttpServletRequest
  ↓
등록된 Filter
  ↓
실제 DispatcherServlet
  ↓
실제 HandlerMapping
  ↓
실제 HandlerInterceptor
  ↓
실제 Controller
  ↓
실제 HandlerMethodArgumentResolver
  ↓
실제 HttpMessageConverter / ViewResolver
  ↓
MockHttpServletResponse
  ↓
andExpect()으로 결과 검증
* */

// @SpringBootTest는 스프링 애플리케이을 실행하기 위한 모든 정보를 loading하기 때문에 무겁다.
// 반면 @WebMvcTest는 Spring MVC 테스트에 필요한 부분만 로딩하기 때문에 훨신 가볍다.
@WebMvcTest(controllers = {
        LoginController.class,
        LoginSessionMvcTest.ProtectedRouteController.class
})
// MockMvc를 자동으로 구성하는데, 스프링 시큐리티와 같은 필터등이 자동으로 개입되지 않도록 방지한다.
@AutoConfigureMockMvc(addFilters = false)
// @WebMvcTest가 추가하지 않는 InterceptorConfig를 명시적으로 추가한다.
@Import({InterceptorConfig.class, LoginSessionMvcTest.ProtectedRouteController.class})
class LoginSessionMvcTest {

    // MockMvc는 실제 톰캣 서버를 띄우지 않고, HTTP 요청을 보내고, 응답을 받을 수 있는 객체이다.
    @Autowired
    private MockMvc mockMvc;

    // 실제 UserService 대신 Mockito mock 객체를 Spring 컨텍스트에 등록한다.(컨트롤러 생성시에 UserService 객체를 연관관계 주입해줘야 하기 때문에 가짜 객체를 생성한다.)
    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("AUTH-002: 로그인 성공 시 인증 사용자를 서버 세션에 저장한다")
    void storesAuthenticatedUserInServerSessionAfterSuccessfulLogin() throws Exception {
        // Mockito에게 userService.login()이 호출되면 실제 로그인 로직을 수행하지 말고, 테스트용 User 객체를 반환하라고 지시한다.
        when(userService.login(any())).thenReturn(user());

        // MockHttpSession은 HttpSession 인터페이스를 구현한 테스트용 객체이다.
        // 실제 애플리케이션에서는 톰캣과 같은 서블릿컨테이너가 HttpSession의 실제 구현체를 만든다.
        // 하지만 WebMvcTest를 수행하게 되면 톰캣과 같은 서블릿컨테이너가 없기 때문에, 대신 MockHttpSession이라는 테스트용 HttpSession의 구현체를 활용한다.

        // 실제 브라우저가 로그인폼을 제출한것처럼 Post /login 요청을 파라미터와 함께 전송한다.
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .param("userId", "finmate01")
                        .param("password", "password1!"))
                .andExpect(status().isFound()) // HTTP 응답메세지가 302 Found인지 검사한다(로그인 성공 시 /로 리다이렉트된다)
                .andExpect(redirectedUrl("/"))
                .andReturn() // 로그인 성공 시 세션이 생성된다.
                .getRequest()
                .getSession(false); // 요청 처리 결과에 세션을 가져온다(이때 세션이 있으면 반환, 없으면 null을 리턴한다)

        assertThat(session).isNotNull(); // session이 null이 아닌지를 확인한다.(로그인 처리 중 실제 세션이 생성되었는지 검증한다.)
        assertThat(session.getAttribute(Const.LOGIN_USER)) // 세션의 Const.LOGIN_USER 키에 저장된 값 SessionUser 타입인지 검사한다.
                .isInstanceOfSatisfying(SessionUser.class, authenticatedUser -> {
                    assertThat(authenticatedUser.getId()).isEqualTo(1L);
                    assertThat(authenticatedUser.getUserId()).isEqualTo("finmate01"); // 세션에 올바른 사용자가 저장되어있는지를 검사한다.
                });
    }

    @Test
    @DisplayName("AUTH-002: 로그인 세션은 연속된 보호 경로 요청에서 재사용된다")
    void reusesSuccessfulLoginSessionForConsecutiveProtectedRequests() throws Exception {
        // Mockito에게 userService.login()이 호출되면 실제 로그인 로직을 수행하지 말고, 테스트용 User 객체를 반환하라고 지시한다.
        when(userService.login(any())).thenReturn(user());
        MockHttpSession session = login(); // MockMvc를 통해 테스트계정을 기반으로 로그인 수행 (세션 생성)

        // 로그인 성공으로 만들어진 하나의 세션을 여러번의 보호 요청에서 계속 재사용할 수 있는지를 검사한다.
        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("authenticated"));

        mockMvc.perform(get("/protected").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("authenticated"));

        //Mockito의 verify()는 해당 메서드가 실제로 호출되었는지를 검사한다. 이때 별도의 횟수를 지정하지 않으면 정확히 한번만 호출되었는지를 검사한다.
        verify(userService).login(any());
    }

    @Test
    @DisplayName("AUTH-002: 알 수 없는 세션 식별자의 보호 경로 요청은 로그인 페이지로 이동한다")
    void redirectsProtectedRequestWhenSessionIdentifierIsUnknown() throws Exception {
        // 로그인하지 않은 상태로 권한이 필요한 페이지에 접근시에 로그인페이지로 리다이렉트되면서 쿼리 파라미터로 로그인후 리다이렉트될 url을 설정한다.
        mockMvc.perform(get("/protected")
                        .cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "unknown-session")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?redirectURL=/protected&message=loginFirst"));
    }

    @Test
    @DisplayName("AUTH-003: 로그아웃은 기존 로그인 세션을 무효화한다")
    void invalidatesSessionOnLogout() throws Exception {
        when(userService.login(any())).thenReturn(user());
        MockHttpSession session = login(); // 테스트계정으로 로그인을 수행한다.

        // 로그아웃 요청을 수행하고 나면, 홈페이지로 리다이렉트된다.
        mockMvc.perform(post("/logout").session(session)) // 로그아웃 요청 기존 로그인 세션을 함께 전달한다.
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));

        // 로그아웃 이후 MockHttpSession이 실제로 무효화되었는지를 검사한다.
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("AUTH-003: 로그아웃 후 기존 세션으로 보호 경로에 접근할 수 없다")
    void redirectsProtectedRequestAfterLogoutInvalidatesSession() throws Exception {
        when(userService.login(any())).thenReturn(user());

        MockHttpSession session = login(); // 테스트계정으로 로그인 수행
        String invalidatedSessionId = session.getId();

        mockMvc.perform(post("/logout").session(session)) // 로그아웃 수행
                .andExpect(status().isFound());

        // 로그아웃한 이후에 권한이 필요한 페이지에 접근시 로그인페이지로 리다이렉트되어야 한다.
        mockMvc.perform(get("/protected")
                        .cookie(new jakarta.servlet.http.Cookie("JSESSIONID", invalidatedSessionId)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?redirectURL=/protected&message=loginFirst"));
    }

    // MockMvc를 기반으로 로그인 수행
    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/login")
                        .param("userId", "finmate01")
                        .param("password", "password1!"))
                .andExpect(status().isFound())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    // 테스트용 User객체
    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUserId("finmate01");
        user.setUsername("Fin Mate");
        return user;
    }

    // /protected url에 대해 처리할 수 있는 테스트용 컨트롤러를 추가한다.
    @Controller
    static class ProtectedRouteController {

        @GetMapping("/protected")
        @ResponseBody
        String protectedPage(HttpSession session) {
            return "authenticated";
        }
    }
}
