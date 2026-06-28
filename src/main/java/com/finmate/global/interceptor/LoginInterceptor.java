package com.finmate.global.interceptor;

import com.finmate.global.Const;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {

    // handler 호출 전에 사용자 로그인 여부를 검증해야 한다.
    // 만약 로그인하지 않은 사용자가 권한이 없는 페이지 접근 시 로그인 페이지로 이동한 후, 로그인 성공시 원래 페이지로 다시 리다이렉트된다.
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false); // session이 없을때 새로 생성 x

        if (session == null || session.getAttribute(Const.LOGIN_USER) == null) {
            // 원래 페이지로 redirect할 url을 전달해야 하는데, login 페이지로 redirect된다면 Model에 redirect url을 담을 수 없다.
            // 따라서 redirect url은 쿼리 파라미터로 따로 경로에 전달해야 한다.
            // 오류 메세지도 redirect url과 마찬가지로 쿼리 파라미터로 전달해야 한다.
            String redirectURL = request.getRequestURI();
            String message = "loginFirst";
            // login페이지로 redirect시킬 때, 원래 이동하고자 했던 원본 주소 정보도 추가한다. 또한 loginFirst라는 문구도 view에 렌더링한다.
            response.sendRedirect("/login?"+"redirectURL="+redirectURL+"&message="+message);
            return false;
        }

        return true;
    }
}
