package com.finmate.controller.home;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j // Lombok이 제공하는 로깅 에노테이션 -> log를 쉽고 빠르게 설정할 수 있는 에노테이션
@Controller // Component scan대상으로 설정
@RequestMapping("/") // "/" 경로로 들어오는 모든 요청을 처리
@RequiredArgsConstructor // 생성자 자동생성(final 멤버들을 모아서 생성자 자동생성)
public class HomeController {

    @GetMapping
    public String homeRedirect(){
        return "redirect:/home"; // 핸들러어뎁터가 이를 redirect로 처리한다.
    }

    @GetMapping("home")
    public String home(){
        return "home/home"; // 핸들러어뎁터가 Thymeleaf 뷰 이름을 기반으로 Thymeleaf view를 렌더링한다.
    }
}
