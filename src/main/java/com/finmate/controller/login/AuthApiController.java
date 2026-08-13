package com.finmate.controller.login;

import com.finmate.domain.user.dto.SignupRequest;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final UserService userService;

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

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.save(request);
        return ResponseEntity.noContent().build();
    }

    public record AuthOptions(boolean google, boolean kakao, boolean naver) {
    }
}
