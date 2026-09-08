package com.finmate.controller.user;

import com.finmate.domain.user.dto.ChangePasswordRequest;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserAccountApiController {

    private final UserService userService;

	// 비밀번호 변경을 요청하는 API
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
			// 이미 로그인한 사용자들만 패스워드 변경이 가능하며, 이미 로그인한 사용자들은 AuthenticationPrincipal에 로그인한 세션정보가 저장되어 있기 떄문에 해당 세션정보를 활용하여 사용자 정보를 활용한다.
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        // 요청 본문의 사용자 식별값을 신뢰하지 않고 인증된 세션의 내부 사용자 ID만 사용한다.
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
