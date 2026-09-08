package com.finmate.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest implements LoginDTO{

    @NotBlank(message = "사용자 이름값은 필수입니다.")
    private String username;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^(?:01[0-9]-[0-9]{4}-[0-9]{4}|01[0-9][0-9]{8})$", message = "전화번호 형식은 010-0000-0000 형식입니다.")
    private String telephone;

    @NotBlank(message = "이메일은 필수입니다.")
    @Pattern(regexp = "^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$", message = "올바른 이메일 형식을 입력해주세요.")
    private String email;

    @NotBlank(message = "아이디는 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9]{8,20}", message = "아이디에는 숫자,영문자만 사용 가능하며 최소 길이는 8, 최대 길이는 20이어야 합니다.")
    private String userId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[!@#$%^&*]).{10,}$", message = "비밀번호에는 영어,숫자,특수문자가 모두 포함되어야 하며 최소 길이는 10이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirmation;
}
