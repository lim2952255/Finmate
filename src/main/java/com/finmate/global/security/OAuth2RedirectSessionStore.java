package com.finmate.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

// OAuth 시작 전의 React 화면과 OAuth callback은 서로 다른 HTTP 요청이다.
// 두 요청 사이에서 원래 화면 경로를 잃지 않도록 같은 HttpSession에 잠시 보관하는 클래스다.
// Spring Security가 자동 관리하는 SavedRequest와는 다른 FinMate 전용 값이다.
public class OAuth2RedirectSessionStore {
    static final String SESSION_ATTRIBUTE = OAuth2RedirectSessionStore.class.getName() + ".target";
    private static final String DEFAULT_TARGET = "/home"; // 기본 주소는 home으로 설정한다.

    public void save(HttpServletRequest request, String target) {
        // 기존 세션이 있으면 재사용하고, 없으면 OAuth callback까지 값을 유지하기 위한 비인증 세션을 만든다.
        // 비인증 세션에는 복귀 경로만 들어 있으며 로그인 권한은 없다. 로그인 여부는 SecurityContext가 결정한다.
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, sanitize(target));
    }

    public String consume(HttpServletRequest request) {
        // 브라우저가 callback 요청에 OAuth 시작 때 발급된 JSESSIONID 쿠키를 보내므로 서버는 같은 HttpSession을 찾을 수 있다.
        // 시작 요청 없이 callback만 들어오면 새 세션을 만들지 않고 기본 경로를 사용한다.
        HttpSession session = request.getSession(false);
        if (session == null) {
            return DEFAULT_TARGET;
        }

        Object storedTarget = session.getAttribute(SESSION_ATTRIBUTE);
        // 이전 로그인 목적지가 다음 OAuth 시도에 재사용되지 않도록 한 번 읽은 값은 제거한다.
        session.removeAttribute(SESSION_ATTRIBUTE);
        return storedTarget instanceof String target ? sanitize(target) : DEFAULT_TARGET;
    }

    private String sanitize(String target) {
        // base URL은 서버 설정에서만 가져오고, 클라이언트는 FinMate 내부 절대 경로만 지정할 수 있다.
        if (target == null || !target.startsWith("/") || target.startsWith("//")) {
            return DEFAULT_TARGET;
        }
        return target;
    }
}
