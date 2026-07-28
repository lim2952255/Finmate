package com.finmate.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 여러 컨트롤러에 공통으로 적용되는 기능 정의. 즉 모든 컨트롤러에서 model에 로그인한 사용자 정보를 주입해주는 역할을 한다.
@ControllerAdvice
public class AuthenticatedUserModelAdvice {

    // 로그인한 사용자 정보를 모든 MVC 화면의 Model에 자동으로 넣어주는 설정이다.
    // 이때 model의 "user" 속성에 로컬 로그인 또는 OIDC 로그인 Principal을 주입한다.
    // FinMateAuthenticatedPrincipal은 일반 로그인 Principal과 OAuth 로그인 Principal을 모두 처리할 수 있도록 FinMateAuthenticatedPrincipal을 사용한다.
    @ModelAttribute("user")
    public FinMateAuthenticatedPrincipal authenticatedUser(
            // @AuthenticationPrincipal은 Spring Sequrity가 현재 인증된 사용자의 Principal을 꺼내서 넣어준다.
            /*
            * SecurityContext
            * └─ Authentication
            * ├─ principal: FinMatePrincipal
            * ├─ authorities: ROLE_USER
            * └─ authenticated: true
            *
            * Spring Sequrity에서는 SecurityContext내에서 인증된 사용자 정보를 관리하며, @AuthenticationPrincipal은 SecurityContext에서 principal을 꺼내서 주입해준다.
            * */
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal
    ) {
        return principal;
    }
}
