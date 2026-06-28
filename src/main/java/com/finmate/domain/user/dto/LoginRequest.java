package com.finmate.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// DTO는 외부와 주고받아야 하는 필드값만 노출시키기 위해서 필요한 필드만 따로 꺼내서 저장하는 것이다.
@Getter
@Setter
public class LoginRequest implements LoginDTO{

    @NotBlank(message = "아이디는 필수입니다.")
    private String userId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
