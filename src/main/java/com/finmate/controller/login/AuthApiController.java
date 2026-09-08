package com.finmate.controller.login;

import com.finmate.domain.user.dto.FindUserIdRequest;
import com.finmate.domain.user.dto.FindUserIdResponse;
import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.global.security.OAuth2RedirectSessionStore;
import com.finmate.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final UserService userService;
    private final OAuth2RedirectSessionStore oauth2RedirectSessionStore; //

    @Value("${finmate.oauth.google.enabled:false}")
    private boolean googleOAuthEnabled;
    @Value("${finmate.oauth.kakao.enabled:false}")
    private boolean kakaoOAuthEnabled;
    @Value("${finmate.oauth.naver.enabled:false}")
    private boolean naverOAuthEnabled;

    @GetMapping("/options")
    public AuthOptions options() {
        return new AuthOptions(googleOAuthEnabled, kakaoOAuthEnabled, naverOAuthEnabled);
    }

    // React에서 OAuth 버튼을 누르면 Vite/Nginx가 /api 요청을 Spring으로 전달하고 이 메서드가 호출된다.
    // redirect는 React가 로그인 전에 기억한 화면 경로이다. 예: /investments/portfolio
    @GetMapping("/oauth2/{registrationId}")
    public void startOAuth2Login(
            @PathVariable String registrationId, // google, kakao와 같은 OAuth 제공자 아이디
            @RequestParam(defaultValue = "/home") String redirect,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        if (!isOAuthEnabled(registrationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // /api/auth/**와 /oauth2/**는 permitAll 경로이므로 이 요청 자체는 Spring Security RequestCache가 저장하지 않는다.
        // 대신 우리 코드가 React에서 받은 원래 경로만 FinMate 전용 session attribute에 직접 저장한다.
        oauth2RedirectSessionStore.save(request, redirect);
        // 복귀 경로를 저장한 뒤 Spring Security가 제공하는 표준 OAuth 시작 경로로 보낸다.
        response.sendRedirect(request.getContextPath() + "/oauth2/authorization/" + registrationId);
    }

	// 회원가입 요청 API
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.save(request);
        return ResponseEntity.noContent().build();
    }

	// 사용자 아이디 찾기 API
    @PostMapping("/find-id")
    public FindUserIdResponse findUserId(@Valid @RequestBody FindUserIdRequest request) {
        // OAuth 전용 사용자는 로그인 아이디가 없으므로 서비스에서 로컬 계정만 조회한다.
        return new FindUserIdResponse(userService.findUserId(request));
    }

    private boolean isOAuthEnabled(String registrationId) {
        return switch (registrationId) {
            case "google" -> googleOAuthEnabled;
            case "kakao" -> kakaoOAuthEnabled;
            case "naver" -> naverOAuthEnabled;
            default -> false;
        };
    }

    public record AuthOptions(boolean google, boolean kakao, boolean naver) {
    }
}
