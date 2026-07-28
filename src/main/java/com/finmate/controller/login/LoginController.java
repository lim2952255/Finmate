package com.finmate.controller.login;

import com.finmate.domain.user.dto.LoginRequest;
import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.exception.DuplicatedId;
import com.finmate.service.user.UserService;
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

    // login 컨트롤러에서는 GET 요청만 처리한다.
    // login에 대한 POST 요청은 이제 Spring Sequrity login filter를 통해서 수행한다.
    @GetMapping("/login")
    public String login(Model model) {
        // 빈 DTO 객체를 생성 후 model에 담아서 view 호출
        model.addAttribute("loginRequest", new LoginRequest());
        return "home/login";
    }
}
