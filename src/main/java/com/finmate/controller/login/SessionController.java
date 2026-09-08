package com.finmate.controller.login;

import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// React Header가 현재 로그인 여부와 로그아웃용 CSRF 토큰을 조회하는 REST Controller다.
@RestController
@RequestMapping("/api/session")
public class SessionController {

    @GetMapping
    public SessionResponse session(
            // 브라우저가 JSESSIONID 쿠키를 함께 보내므로 Spring Security가 현재 사용자를 주입할 수 있다.
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        // 로그아웃 form POST에 필요한 CSRF 토큰도 같은 JSON 응답에 포함한다.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        CsrfResponse csrf = new CsrfResponse(
                csrfToken.getParameterName(),
                csrfToken.getHeaderName(),
                csrfToken.getToken()
        );

        // 비로그인 사용자는 principal이 null이다.
        if (principal == null) {
            return new SessionResponse(false, null, false, csrf);
        }

        // 로그인 아이디가 있는 로컬 계정에만 비밀번호 변경 화면을 노출한다.
        return new SessionResponse(
                true,
                principal.getDisplayName(),
                principal.getUserId() != null,
                csrf
        );
    }

    // 세션 로그인 정보를 리턴한다.
    public record SessionResponse(
            boolean authenticated,
            String displayName,
            boolean passwordChangeAvailable, // 로컬 사용자 계정만 패스워드 변경이 가능하다. OAuth 사용자는 패스워드 변경이 불가능하다.
            CsrfResponse csrf
    ) {
    }

    // CSRF 토큰정보를 리턴한다.
    public record CsrfResponse(
            String parameterName,
            String headerName,
            String token
    ) {
    }
}
