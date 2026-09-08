package com.finmate.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 비밀번호 변경용 프론트엔드 페이지에 제공할 DTO.
// 해당 DTO를 프론트로 보내면, 프론트에서 사용자 입력정보를 기반으로 해당 DTO에 필드정보를 채우고, 값을 채운 DTO를 다시 백엔드 서버로 보낸다.
public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[!@#$%^&*]).{10,}$",
                message = "비밀번호에는 영어, 숫자, 특수문자가 모두 포함되어야 하며 최소 길이는 10자 이상이어야 합니다."
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        String passwordConfirmation
) {
}
