package com.finmate.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// FinMatePrincipal 객체는 Spring Security가 로그인한 사용자를 관리하기 위한 객체이다.
// UserDetails는 Spring Security가 사용자 정보를 읽는 인터페이스이다.
// CredentialsContainer는 인증이 끝난 다음, 비밀번호와 같은 개인정보를 지우는 역할을 수행한다.
public final class FinMatePrincipal implements
        UserDetails,
        CredentialsContainer,
        FinMateAuthenticatedPrincipal {

    private final Long id;
    private final String userId;
    private final String displayName;
    private String password;
    private final List<GrantedAuthority> authorities;

    public FinMatePrincipal(Long id,
                            String userId,
                            String displayName,
                            String password,
                            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.password = password;
        // 사용자가 보유한 권한을 관리한다.
        this.authorities = List.copyOf(authorities);
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getUsername() {
        return userId;
    }

    // 패스워드 정보는 인증시점에만 사용하고, 인증이 끝나면 패스워드 정보를 제거한다.
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // 인증이 끝나면 패스워드 정보를 제거한다.
    @Override
    public void eraseCredentials() {
        password = null;
    }
}
