package com.finmate.global.interceptor;

import com.finmate.controller.home.HomeController;
import com.finmate.controller.login.LoginController;
import com.finmate.service.user.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring MVC 요청 처리 흐름을 검증하는 MVC 슬라이스 테스트
// MockMvc는 실제 톰캣 서버를 실행하지 않고도, 가짜 HTTP 요청을 Spring MVC에 보내서 응답을 검증할 수 있게 해주는 테스트 도구이다.

// @WebMvcTest는 Spring 전체를 띄우지 않고 MVC 관련 Bean만 제한적으로 로딩한다.
@WebMvcTest(controllers = {
        HomeController.class,
        LoginController.class,
        LoginInterceptorMvcTest.ProtectedRouteController.class
})
@AutoConfigureMockMvc(addFilters = false) // MockMvc를 사용할 수 있도록 설정하는 애노테이션(이때 MockMvc에 SpringSequrity와 같은 필터들은 붙이지 않는다.(스프링 인터셉터는 붙는다))
@Import({InterceptorConfig.class, LoginInterceptorMvcTest.ProtectedRouteController.class}) // 스프링 인터셉터 설정을 직접 import한다.
class LoginInterceptorMvcTest {

    // @WebMvcTest와 @AutoConfigureMockMvc가 생성한 MockMvc 객체를 주입받는다.
    // MockMvc 객체를 활용해서 가짜 HTTP요청을 보내고, 처리할 수 있다.
    @Autowired
    private MockMvc mockMvc;

    // LoginController가 UserService에 의존하고 있기때문에, 컨트롤러를 정상적으로 생성하기 위해 MockitoBean이라는 가짜 객체를 대신 스프링 빈으로 등록한다.
    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("AUTH-001: 비로그인 사용자는 루트 경로에서 세션 생성 없이 홈으로 이동한다")
    void allowsUnauthenticatedAccessToRootPath() throws Exception {
        mockMvc.perform(get("/")) // MockMvc를 통해 루트 경로로 HTTP get 메세지를 전송한다.
                .andExpect(status().isFound()) // HTTP 응답 코드가 302 Found인지를 검사한다(리다이렉트)
                .andExpect(redirectedUrl("/home")) // 리다이렉트 목적지가 /home인지를 검사한다.
                // 루트페이지에 접속해서 /home으로 리다이렉트되는 과정에서 불필요하게 세션이 생성되지는 않았는지를 검사한다.
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @ParameterizedTest(name = "allows unauthenticated access to public path {0}")
    @DisplayName("AUTH-001: 비로그인 사용자는 공개 경로에 세션 생성 없이 접근할 수 있다")
    @ValueSource(strings = {"/home", "/login", "/signup"}) // test에서 사용할 경로 파라미터를 전달한다.
    void allowsUnauthenticatedAccessToPublicPath(String publicPath) throws Exception {
        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk()) // /home, /login, /signup은 로그인하지 않은 사용자도 접속이 가능해야 한다,
                // 로그인하지 않은 사용자가 해당 페이지들에 접근할때 불필요한 세션이 생성되지는 않았는지를 검사한다.
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("AUTH-001: 비로그인 사용자는 정적 리소스에 세션 생성 없이 접근할 수 있다")
    void allowsUnauthenticatedAccessToStaticResource() throws Exception {
        mockMvc.perform(get("/css/common.css"))
                .andExpect(status().isOk()) // css도 로그인하지 않은 사용자를 차단하며 안된다.
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @ParameterizedTest(name = "redirects unauthenticated access to protected path {0}")
    @DisplayName("AUTH-001: 비로그인 사용자의 보호 경로 요청은 로그인 페이지로 이동한다")
    @ValueSource(strings = {"/accounts", "/investments", "/orders"}) // 계좌페이지, 증권페이지, 주문페이지들은 로그인한 사용자만 접근가능해야 한다.
    void redirectsUnauthenticatedAccessToProtectedPath(String protectedPath) throws Exception {
        mockMvc.perform(get(protectedPath))
                .andExpect(status().isFound()) // 권한없는 페이지에 접근할 경우, HTTP 응답의 상태코드가 302 Found이어야 한다(Redirect)
                // 권한이 없는 페이지에 접속햇을때, 로그인 페이지로 리다이렉트되는지를 검사한다.
                .andExpect(redirectedUrl("/login?redirectURL=" + protectedPath + "&message=loginFirst"))
                // 아직 사용자가 로그인하지 않았기 때문에 세션이 생성되면 안된다.
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    // 현재 테스트에서는 @WebMvcTest에서 LoginController와 HomeController만 불러오기 때문에 /accounts, /investments, /orders 페이지에 대한 컨트롤러가 없어 요청을 처리할 수 없다.
    // 띠리사 /accounts, /investments, /orders 요청을 받을 수 있는 간단한 컨트롤러를 생성한다(실제로는 아무런 기능이 없지만, 오류만 발생하지 않도록 추가한 컨트롤러)
    @Controller
    static class ProtectedRouteController {

        @GetMapping({"/accounts", "/investments", "/orders"})
        String protectedPage(HttpSession session) {
            return "protected";
        }
    }
}
