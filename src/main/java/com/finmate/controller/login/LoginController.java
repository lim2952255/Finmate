package com.finmate.controller.login;

import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.LoginRequest;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.exception.DuplicatedId;
import com.finmate.exception.LoginException;
import com.finmate.global.Const;
import com.finmate.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {
    private final UserService loginService;

    @GetMapping("/signup")
    public String signup(Model model){
        // 빈 DTO 객체를 생성 후 model에 담아서 view 호출
        model.addAttribute("signupRequest", new SignupRequest());
        return "home/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupRequest") SignupRequest signupRequest, BindingResult bindingResult) {
        // Form 입력정보를 바탕으로 사용자 입력 검증 및 회원가입 처리
        if (bindingResult.hasErrors()) {
            return "home/signup";
        }

        Long id = null;

        try{
            id = loginService.save(signupRequest);
        } catch(DuplicatedId duplicatedId){
            bindingResult.rejectValue("userId", "duplicatedId", "아이디가 중복되었습니다. 다시 시도해주세요.");
            return "home/signup";
        } catch (Exception e){
            bindingResult.reject("signupFail", "회원가입에 실패했습니다. 다시 시도해주세요.");
            return "home/signup";
        }

        // Post이후에는 get으로 redirect시켜야 한다.
        log.info("signup request: id={}, userId={}, username={}, email={}", id, signupRequest.getUserId(), signupRequest.getUsername(), signupRequest.getEmail());
        return "redirect:/"; // home으로 redirect처리
    }

    @GetMapping("/login")
    // redirectURL과 message 정보를 쿼리파라미터에서 꺼내서 읽는다. 이후 이를 Model에 담아서 view로 전달한다.
    public String login(@RequestParam(value="redirectURL", required = false) String redirectURL,
                        @RequestParam(value="message", required = false) String message,
                        Model model) {
        // 빈 DTO 객체를 생성 후 model에 담아서 view 호출

        model.addAttribute("loginRequest", new LoginRequest());
        model.addAttribute("redirectURL", redirectURL); // redirect정보를 추가
        if(message != null){
            model.addAttribute("message","해당 서비스는 로그인이 필요한 서비스입니다.");
        }
        return "home/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequest") LoginRequest loginRequest, BindingResult bindingResult,
                        HttpServletRequest httpServletRequest) {
        // 입력 검증 오류
        if (bindingResult.hasErrors()) {
            return "home/login";
        }

        User loginUser = null;
        try {
            loginUser = loginService.login(loginRequest);
        } catch (LoginException e) {
            bindingResult.reject("loginFail", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "home/login";
        }

        // 로그인 처음 성공시 HttpSession에 로그인 정보를 담는다.
        HttpSession session = httpServletRequest.getSession();
        session.setAttribute(Const.LOGIN_USER, new SessionUser(loginUser));

        String redirectURL = httpServletRequest.getParameter("redirectURL");

        // 만약 로그인을 하기전에 접근하고자 했던 경로가 있었다면 해당 경로로 redirect시킨다.
        // 또한 Post 이후에는 Get으로 Redirect시켜야 한다.
        if(redirectURL != null){
            return "redirect:" + redirectURL;
        }

        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            session.invalidate(); // 세션을 만료시킴으로서 로그아웃 기능을 구현
        }

        return "redirect:/";
    }
}
