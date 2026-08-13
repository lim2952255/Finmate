package com.finmate.controller.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 로그인과 회원가입의 최초 문서 요청을 React 진입 문서로 전달한다.
@Controller
public class LoginController {

    @GetMapping({"/login", "/signup"})
    public String reactEntry() {
        return "forward:/react/index.html";
    }
}
