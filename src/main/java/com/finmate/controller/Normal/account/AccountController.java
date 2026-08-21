package com.finmate.controller.normal.account;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 일반 계좌의 React 화면 진입 URL만 담당한다. 데이터와 변경 요청은 /api 하위 Controller가 처리한다.
@Controller
@RequestMapping("/accounts")
public class AccountController {

    @GetMapping({"", "/open", "/list", "/transfer", "/transfer-investment", "/transfer-limit", "/transactions"})
    public String reactEntry() {
        return "forward:/react/index.html";
    }
}
