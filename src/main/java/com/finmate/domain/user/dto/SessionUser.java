package com.finmate.domain.user.dto;

import com.finmate.domain.user.User;
import lombok.Getter;

@Getter
public class SessionUser implements LoginDTO{

    private final Long id;
    private final String userId;
    private final String username;

    // user 정보를 받아 필요한 정보만 담는 객체 생성
    public SessionUser(User user) {
        this.id = user.getId();
        this.userId = user.getUserId();
        this.username = user.getUsername();
    }
}
