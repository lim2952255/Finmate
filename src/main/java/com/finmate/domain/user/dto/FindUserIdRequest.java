package com.finmate.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 사용자 ID 찾기용 프론트엔드 페이지에 제공할 DTO.
// 해당 DTO를 프론트로 보내면, 프론트에서 사용자 입력정보를 기반으로 해당 DTO에 필드정보를 채우고, 값을 채운 DTO를 다시 백엔드 서버로 보낸다.
public record FindUserIdRequest(
        @NotBlank(message = "사용자 이름값은 필수입니다.")
        String username,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^(?:01[0-9]-[0-9]{4}-[0-9]{4}|01[0-9][0-9]{8})$",
                message = "전화번호 형식은 010-0000-0000 또는 01000000000 형식입니다."
        )
        String telephone,

        @NotBlank(message = "이메일은 필수입니다.")
        @Pattern(
                regexp = "^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$",
                message = "올바른 이메일 형식을 입력해주세요."
        )
        String email
) {
}
